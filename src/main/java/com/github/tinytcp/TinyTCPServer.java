package com.github.tinytcp;

import java.lang.Thread.UncaughtExceptionHandler;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * A minimal TCP Server that cuts through all the nonsense and means business.
 * 
 * @author gaurav
 */
public final class TinyTCPServer {
  private static final Logger logger = LogManager.getLogger(TinyTCPServer.class.getSimpleName());
  private final String id = new RandomIdProvider().id();

  private Channel serverChannel;
  private EventLoopGroup eventLoopThreads;
  private EventLoopGroup workerThreads;
  private boolean running;

  // TODO: use a builder
  private int eventLoopThreadCount = 1;
  private int workerThreadCount = 4;
  private String serverHost;
  private int serverPort;

  private final AtomicInteger currentActiveConnections = new AtomicInteger();
  private final AtomicLong allAcceptedConnections = new AtomicLong();
  private final AtomicLong allConnectionIdleTimeouts = new AtomicLong();
  private final AtomicLong allRequestsReceived = new AtomicLong();
  private final AtomicLong allResponsesSent = new AtomicLong();

  public TinyTCPServer(final String serverHost, final int serverPort) {
    Objects.requireNonNull(serverHost, "serverHost cannot be null");
    this.serverHost = serverHost;
    this.serverPort = serverPort;
  }

  // just don't mess with the lifecycle methods
  public synchronized void start() throws Exception {
    final long startNanos = System.nanoTime();
    logger.info("Starting tiny tcp server [{}] at {}:{}", id, serverHost, serverPort);
    eventLoopThreads = new NioEventLoopGroup(eventLoopThreadCount, new ThreadFactory() {
      private final AtomicInteger threadCounter = new AtomicInteger();

      @Override
      public Thread newThread(final Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setName("server-loop-" + threadCounter.getAndIncrement());
        thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
          @Override
          public void uncaughtException(Thread thread, Throwable error) {
            logger.error("Logging unhandled exception", error);
          }
        });
        return thread;
      }
    });
    workerThreads = new NioEventLoopGroup(workerThreadCount, new ThreadFactory() {
      private final AtomicInteger threadCounter = new AtomicInteger();

      @Override
      public Thread newThread(final Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setName("server-worker-" + threadCounter.getAndIncrement());
        thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
          @Override
          public void uncaughtException(Thread thread, Throwable error) {
            logger.error("Logging unhandled exception", error);
          }
        });
        return thread;
      }
    });
    final ServerBootstrap serverBootstrap = new ServerBootstrap();
    serverBootstrap.group(eventLoopThreads, workerThreads).channel(NioServerSocketChannel.class);
    serverBootstrap.childOption(ChannelOption.TCP_NODELAY, true);
    serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
    // serverBootstrap.childOption(ChannelOption.SO_BACKLOG, 1024);
    serverBootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
    serverBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
    serverBootstrap.handler(new LoggingHandler(LogLevel.INFO));

    // TODO: switch to using a ChannelInitializer implementation
    final ConnectionMetricHandler connectionMetricHandler = new ConnectionMetricHandler(
        currentActiveConnections, allAcceptedConnections, allConnectionIdleTimeouts);
    serverBootstrap.handler(connectionMetricHandler);

    serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
      protected void initChannel(SocketChannel socketChannel) throws Exception {
        socketChannel.pipeline().addLast(new TinyTCPServerHandler());
      }
    });
    serverChannel = serverBootstrap.bind(serverHost, serverPort).sync().channel();

    final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    if (isChannelHealthy()) {
      running = true;
      logger.info("Started tiny tcp server [{}] at {} in {} millis", id,
          serverChannel.localAddress(), elapsedMillis);
    } else {
      logger.info("Failed to start tiny tcp server [{}] at {}:{} in {} millis", id, serverHost,
          serverPort, elapsedMillis);
    }
  }

  // just don't mess with the lifecycle methods
  public synchronized void stop() throws Exception {
    final long startNanos = System.nanoTime();
    if (!running) {
      logger.info("Cannot stop an already stopped server [{}]", id);
    }
    logger.info(
        "Stopping tiny tcp server [{}] at {} :: allAcceptedConnections:{}, allRequestsReceived:{}",
        id, serverChannel.localAddress(), allAcceptedConnections.get(), allRequestsReceived.get());
    if (serverChannel != null) {
      serverChannel.close().await();
    }
    if (eventLoopThreads != null) {
      eventLoopThreads.shutdownGracefully().await();
    }
    if (workerThreads != null) {
      workerThreads.shutdownGracefully().await();
    }
    if (serverChannel != null) {
      serverChannel.closeFuture().await();
    }
    running = false;
    final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    logger.info("Stopped tiny tcp server [{}] at {}:{} in {} millis", id, serverHost, serverPort,
        elapsedMillis);
  }

  private boolean isChannelHealthy() {
    return serverChannel.isOpen() && serverChannel.isActive();
  }

  public boolean isRunning() {
    return running;
  }

  public String getId() {
    return id;
  }

  public long getAllRequestsReceived() {
    return allRequestsReceived.get();
  }

  public long getAllResponsesSent() {
    return allResponsesSent.get();
  }

  // TODO: externalize
  public Response serviceRequest(final Request request) {
    final Response response = new TinyResponse();
    ((TinyResponse) response).setRequestId(request.getId());
    return response;
  }

  /**
   * Figure the server-side business-logic here.
   * 
   * @author gaurav
   */
  public class TinyTCPServerHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(final ChannelHandlerContext context, final Object msg)
        throws Exception {
      final long startNanos = System.nanoTime();
      allRequestsReceived.incrementAndGet();
      final ByteBuf payload = (ByteBuf) msg;
      final byte[] requestBytes = ByteBufUtil.getBytes(payload);
      final Request request = new TinyRequest().deserialize(requestBytes);
      final SocketAddress client = context.channel().remoteAddress();
      logger.info("Server [{}] received {} type {} from {}", id, request, msg.getClass().getName(),
          client);

      final Response response = serviceRequest(request);
      final byte[] serializedResponse = response.serialize();

      context.writeAndFlush(Unpooled.copiedBuffer(serializedResponse));
      allResponsesSent.incrementAndGet();
      final long elapsedMicros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startNanos);
      logger.info("Server [{}] responded to client {} with response: {} in {} micros", id, client,
          response, elapsedMicros);
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext context) throws Exception {
      logger.info("Server [{}] channel read complete", id);
      context.flush();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause)
        throws Exception {
      logger.error(cause);
      context.close();
    }

  }

}

