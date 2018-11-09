package com.github.tinytcp;

import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * A minimal TCP Client that cuts through all the nonsense and means business.
 * 
 * @author gaurav
 */
public final class TinyTCPClient {
  private static final Logger logger = LogManager.getLogger(TinyTCPClient.class.getSimpleName());
  private final String id = new RandomIdProvider().id();

  private final ConcurrentMap<InetSocketAddress, Channel> trackedServers =
      new ConcurrentHashMap<>();
  private EventLoopGroup clientThreads;
  private boolean running;

  // TODO: use a builder
  private int workerThreadCount = 2;
  private final InetSocketAddress serverAddress;

  private final boolean localServer;

  private final AtomicLong allRequestsSent = new AtomicLong();
  private final AtomicLong allResponsesReceived = new AtomicLong();

  // TODO: add support for local server
  public TinyTCPClient(final InetSocketAddress serverAddress, final boolean localServer) {
    Objects.requireNonNull(serverAddress, "serverAddress cannot be null");
    this.serverAddress = serverAddress;
    this.localServer = localServer;
  }

  // TODO: client:server = 1:n
  // addServerConnections(List<ServerAddress>), dropServerConnections(List<ServerAddress>)

  // do not mess with the lifecycle
  public synchronized void start() throws Exception {
    final long startNanos = System.nanoTime();
    logger.info("Starting tiny tcp client [{}], connecting to {}", id, serverAddress);
    final Bootstrap clientBootstrap = new Bootstrap();
    clientThreads = new NioEventLoopGroup(workerThreadCount, new ThreadFactory() {
      private final AtomicInteger threadCounter = new AtomicInteger();

      @Override
      public Thread newThread(final Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setName("client-worker-" + threadCounter.getAndIncrement());
        thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
          @Override
          public void uncaughtException(Thread thread, Throwable error) {
            logger.error("Logging unhandled exception", error);
          }
        });
        return thread;
      }
    });
    clientBootstrap.group(clientThreads).channel(NioSocketChannel.class);
    clientBootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
    clientBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
    clientBootstrap.handler(new LoggingHandler(LogLevel.INFO));
    clientBootstrap.handler(new ChannelInitializer<SocketChannel>() {
      protected void initChannel(SocketChannel socketChannel) throws Exception {
        socketChannel.pipeline().addLast(new TinyTCPClientHandler());
      }
    });
    final Channel clientChannel = clientBootstrap
        .connect(serverAddress.getHostName(), serverAddress.getPort()).sync().channel();

    final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    if (isChannelHealthy(clientChannel)) {
      trackedServers.put(serverAddress, clientChannel);
      running = true;
      logger.info("Started tiny tcp client [{}] connected to {} in {} millis", id,
          clientChannel.remoteAddress(), elapsedMillis);
    } else {
      logger.info("Failed to start tiny tcp client [{}] to connect to {} in {} millis", id,
          serverAddress, elapsedMillis);
    }
  }

  // no messing with the lifecycle
  public synchronized void stop() throws Exception {
    final long startNanos = System.nanoTime();
    if (!running) {
      logger.info("Cannot stop an already stopped client [{}]", id);
    }
    for (final Channel clientChannel : trackedServers.values()) {
      logger.info(
          "Stopping tiny tcp client [{}] connected to {} :: allRequestsSent:{}, allResponsesReceived:{}",
          id, clientChannel.remoteAddress(), allRequestsSent.get(), allResponsesReceived.get());
      if (clientChannel != null) {
        clientChannel.close().await();
      }
      if (clientThreads != null) {
        clientThreads.shutdownGracefully().await();
      }
      if (clientChannel != null) {
        clientChannel.closeFuture().await().await();
      }
      running = false;
      final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
      logger.info("Stopped tiny tcp client [{}] connected to {} in {} millis", id,
          clientChannel.remoteAddress(), elapsedMillis);
    }
  }

  private boolean isChannelHealthy(final Channel channel) {
    return channel != null && channel.isOpen() && channel.isActive();
  }

  public boolean isRunning() {
    return running;
  }

  public String getId() {
    return id;
  }

  // TODO: externalize
  public boolean sendToServer(final InetSocketAddress server, final Request request) {
    if (!running) {
      logger.error("Cannot pipe a request down a stopped client");
      return false;
    }
    final Channel clientChannel = trackedServers.get(server);
    if (!isChannelHealthy(clientChannel)) {
      logger.error("Cannot pipe a request down a stopped channel");
      return false;
    }
    allRequestsSent.incrementAndGet();
    logger.info("Client [{}] sending to server, payload: {}", id, request);
    final byte[] serializedRequest = request.serialize();
    final ChannelFuture future =
        clientChannel.writeAndFlush(Unpooled.copiedBuffer(serializedRequest));
    future.awaitUninterruptibly();
    return future.isDone();
  }

  // TODO: externalize
  public void handleServerResponse(final Response response) {
    logger.info("Client [{}] received from server, response: {}", id, response);
  }

  /**
   * Handler for processing client-side I/O events.
   * 
   * @author gaurav
   */
  public class TinyTCPClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    @Override
    public void channelActive(final ChannelHandlerContext channelHandlerContext) {
      logger.info("Client [{}] is active", id);
    }

    @Override
    public void channelRead0(final ChannelHandlerContext channelHandlerContext,
        final ByteBuf payload) {
      allResponsesReceived.incrementAndGet();
      final byte[] responseBytes = ByteBufUtil.getBytes(payload);
      final Response response = new TinyResponse().deserialize(responseBytes);
      handleServerResponse(response);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext channelHandlerContext,
        final Throwable cause) {
      logger.error(cause);
      channelHandlerContext.close();
    }

  }

}
