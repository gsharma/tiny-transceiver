package com.github.tinytcp;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
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
  private final String id = UUID.randomUUID().toString();

  private Channel serverChannel;
  private EventLoopGroup eventLoopThreads;
  private EventLoopGroup workerThreads;
  private boolean running;

  // TODO: properties
  private int eventLoopThreadCount = 1;
  private int workerThreadCount = 4;
  private String host = "localhost";
  private int port = 9999;

  private static final AtomicInteger currentActiveConnectionCount = new AtomicInteger();
  private static final AtomicLong allAcceptedConnectionCount = new AtomicLong();
  private static final AtomicLong allConnectionIdleTimeoutCount = new AtomicLong();
  private static final AtomicLong allRequestsServicedCount = new AtomicLong();

  // just don't mess with the lifecycle methods
  public synchronized void start() throws Exception {
    final long startNanos = System.nanoTime();
    logger.info("Starting tiny tcp server [{}]", id);
    eventLoopThreads = new NioEventLoopGroup(eventLoopThreadCount, new ThreadFactory() {
      private final AtomicInteger threadCounter = new AtomicInteger();

      @Override
      public Thread newThread(final Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setName("server-" + threadCounter.getAndIncrement());
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
        thread.setName("worker-" + threadCounter.getAndIncrement());
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
        currentActiveConnectionCount, allAcceptedConnectionCount, allConnectionIdleTimeoutCount);
    serverBootstrap.handler(connectionMetricHandler);

    serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
      protected void initChannel(SocketChannel socketChannel) throws Exception {
        socketChannel.pipeline().addLast(new TinyTCPServerHandler(allRequestsServicedCount));
      }
    });
    serverChannel = serverBootstrap.bind(host, port).sync().channel();
    running = true;
    final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    logger.info("Started tiny tcp server [{}] in {} millis", id, elapsedMillis);
  }

  // just don't mess with the lifecycle methods
  public void stop() throws Exception {
    final long startNanos = System.nanoTime();
    if (!running) {
      logger.info("Cannot stop an already stopped server [{}]", id);
    }
    logger.info(
        "Stopping tiny tcp server:: allAcceptedConnectionCount:{}, allRequestsServicedCount:{}",
        allAcceptedConnectionCount.get(), allRequestsServicedCount.get());
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
    logger.info("Stopped tiny tcp server [{}] in {} millis", id, elapsedMillis);
  }

  public boolean isRunning() {
    return running;
  }

  public String getId() {
    return id;
  }

}

