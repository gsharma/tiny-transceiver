package com.github.tinytcp;

import java.lang.Thread.UncaughtExceptionHandler;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.tinytcp.TinyTCPStateHandler.Type;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4J2LoggerFactory;

/**
 * A minimal TCP Server that cuts through all the nonsense and means business.
 * 
 * @author gaurav
 */
public final class TinyTCPServer {
  private static final Logger logger = LogManager.getLogger(TinyTCPServer.class.getSimpleName());
  private final String id;
  private final IdProvider idProvider;

  private Channel serverChannel;
  private EventLoopGroup eventLoopThreads;
  private EventLoopGroup workerThreads;
  private boolean running;

  // TODO: use a builder
  private int eventLoopThreadCount = 1;
  private int workerThreadCount = 4;
  private final ServerDescriptor serverDescriptor;

  // private final AtomicInteger currentActiveConnections = new AtomicInteger();
  // private final AtomicLong allAcceptedConnections = new AtomicLong();
  // private final AtomicLong allConnectionIdleTimeouts = new AtomicLong();

  private final AtomicLong allRequestsReceived = new AtomicLong();
  private final AtomicLong allResponsesSent = new AtomicLong();

  public TinyTCPServer(final IdProvider idProvider, final ServerDescriptor serverDescriptor) {
    Objects.requireNonNull(idProvider, "idProvider cannot be null");
    Objects.requireNonNull(serverDescriptor, "serverDescriptor cannot be null");
    this.idProvider = idProvider;
    this.id = idProvider.id();
    this.serverDescriptor = serverDescriptor;
  }

  // just don't mess with the lifecycle methods
  public synchronized void start() {
    final long startNanos = System.nanoTime();
    logger.info("Starting server[{}] at {}", id, serverDescriptor);
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
    InternalLoggerFactory.setDefaultFactory(Log4J2LoggerFactory.INSTANCE);
    final ServerBootstrap serverBootstrap = new ServerBootstrap();
    serverBootstrap.group(eventLoopThreads, workerThreads).channel(NioServerSocketChannel.class)
        .handler(new LoggingHandler(LogLevel.INFO)).childHandler(new ChannelInitializer<Channel>() {

          @Override
          public void initChannel(final Channel channel) throws Exception {
            final ChannelPipeline pipeline = channel.pipeline();

            // final ConnectionMetricHandler connectionMetricHandler = new ConnectionMetricHandler(
            // currentActiveConnections, allAcceptedConnections, allConnectionIdleTimeouts);
            pipeline.addLast(new IdleStateHandler(120, 120, 0));
            // pipeline.addLast(connectionMetricHandler);
            pipeline.addLast(new ReadTimeoutHandler(60000L, TimeUnit.MILLISECONDS));
            pipeline.addLast(new WriteTimeoutHandler(60000L, TimeUnit.MILLISECONDS));
            pipeline.addLast(new TinyTCPServerHandler());
            pipeline.addLast(new TinyTCPStateHandler(id, Type.SERVER));
          }
        });

    serverBootstrap.childOption(ChannelOption.TCP_NODELAY, true);
    serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
    serverBootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
    serverBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000);

    try {
      serverChannel = serverBootstrap.bind(serverDescriptor.getAddress().getHostName(),
          serverDescriptor.getAddress().getPort()).sync().channel();
    } catch (InterruptedException problem) {
      logger.error("Failed to connect to " + serverDescriptor, problem);
      return;
    }

    final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    if (isChannelHealthy(serverChannel)) {
      running = true;
      // new ServerSentinel(5L, serverChannel).start();
      logger.info("Started server[{}] at {} in {} millis", id, serverChannel.localAddress(),
          elapsedMillis);
    } else {
      logger.info("Failed to start server[{}] at {} in {} millis", id, serverDescriptor,
          elapsedMillis);
    }
  }

  @SuppressWarnings("unused")
  private final class ServerSentinel extends Thread {
    private final long sleepSeconds;
    private final Channel monitoredChannel;

    private ServerSentinel(final long sleepSeconds, final Channel monitoredChannel) {
      setDaemon(true);
      setName("server-sentinel");
      this.sleepSeconds = sleepSeconds;
      this.monitoredChannel = monitoredChannel;
    }

    @Override
    public void run() {
      while (!interrupted()) {
        if (!isChannelHealthy(monitoredChannel)) {
          logger.error("Server[{}] channel is not healthy", id);
        } else {
          logger.info("Server[{}] channel is healthy", id);
        }
        try {
          sleep(TimeUnit.SECONDS.toMillis(sleepSeconds));
        } catch (InterruptedException e) {
          interrupt();
        }
      }
    }
  }

  // just don't mess with the lifecycle methods
  public synchronized void stop() {
    final long startNanos = System.nanoTime();
    if (!running) {
      logger.info("Cannot stop an already stopped server[{}]", id);
    }
    logger.info("Stopping server[{}] at {}", id, serverChannel.localAddress());
    try {
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
    } catch (InterruptedException problem) {
      logger.error("Encountered a problem while stopping server at " + serverChannel.localAddress(),
          problem);
    }
    running = false;
    final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    logger.info("Stopped server[{}] at {} in {} millis", id, serverDescriptor, elapsedMillis);
  }

  private boolean isChannelHealthy(final Channel channel) {
    return channel != null && channel.isOpen() && channel.isActive() && channel.isWritable();
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
    final Response response = new TinyResponse(idProvider, Optional.ofNullable(request.getId()));
    return response;
  }

  /**
   * Figure the server-side business-logic here.
   * 
   * @author gaurav
   */
  public class TinyTCPServerHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(final ChannelHandlerContext context, final Object msg) {
      final long startNanos = System.nanoTime();
      // allRequestsReceived.incrementAndGet();
      final ByteBuf payload = (ByteBuf) msg;
      final byte[] requestBytes = ByteBufUtil.getBytes(payload);
      final Request request = new TinyRequest(idProvider).deserialize(requestBytes);
      final SocketAddress client = context.channel().remoteAddress();
      // msg.getClass().getName();
      logger.info("Server[{}] received {} from {}", id, request, client);

      final Response response = serviceRequest(request);
      final byte[] serializedResponse = response.serialize();

      allResponsesSent.incrementAndGet();
      final long elapsedMicros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startNanos);
      logger.info("Server[{}] responded to client {} with {} in {} micros", id, client, response,
          elapsedMicros);
      final ChannelFuture future = context.write(Unpooled.copiedBuffer(serializedResponse));
      future.addListener(new ChannelFutureListener() {

        @Override
        public void operationComplete(final ChannelFuture future) throws Exception {
          if (future.isSuccess()) {
            logger.info("Server[{}] write succeeded", id);
            if (logger.isDebugEnabled()) {
              logger.debug("Server[{}] write succeeded", id);
            }
          } else {
            logger.error(String.format("Server[%s] encountered error", id), future.cause());
            future.cause().printStackTrace();
          }
        }
      });
      return;
    }

  }

}

