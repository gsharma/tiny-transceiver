package com.github.tinytcp;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
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
  private Channel clientChannel;
  private EventLoopGroup clientThreads;
  private boolean running;

  // TODO: properties
  private int workerThreadCount = 2;
  private String host = "localhost";
  private int port = 9999;

  // do not mess with the lifecycle
  public synchronized void start() throws Exception {
    logger.info("Starting tiny tcp client");
    final Bootstrap clientBootstrap = new Bootstrap();
    clientThreads = new NioEventLoopGroup(workerThreadCount, new ThreadFactory() {
      private final AtomicInteger threadCounter = new AtomicInteger();

      @Override
      public Thread newThread(final Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setName("client-" + threadCounter.getAndIncrement());
        thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
          @Override
          public void uncaughtException(Thread thread, Throwable error) {
            logger.error("Logging unhandled exception.", error);
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
    clientChannel = clientBootstrap.connect(host, port).sync().channel();
    running = true;
    logger.info("Started tiny tcp client");
  }

  // no messing with the lifecycle
  public synchronized void stop() throws Exception {
    if (!running) {
      logger.info("Cannot stop an already stopped client");
    }
    logger.info("Stopping tiny tcp client");
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
    logger.info("Stopped tiny tcp client");
  }

  public boolean running() {
    return running;
  }

}
