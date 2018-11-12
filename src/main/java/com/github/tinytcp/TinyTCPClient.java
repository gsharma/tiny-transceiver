package com.github.tinytcp;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.tinytcp.TinyTCPStateHandler.Type;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
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
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4J2LoggerFactory;

/**
 * A minimal TCP Client that cuts through all the nonsense and means business.
 * 
 * @author gaurav
 */
public final class TinyTCPClient {
  private static final Logger logger = LogManager.getLogger(TinyTCPClient.class.getSimpleName());

  private final ReentrantReadWriteLock superLock = new ReentrantReadWriteLock(true);
  private final WriteLock writeLock = superLock.writeLock();
  private final ReadLock readLock = superLock.readLock();

  private final AtomicBoolean running = new AtomicBoolean();
  private final ConcurrentMap<ServerDescriptor, Channel> trackedServers = new ConcurrentHashMap<>();
  private final IdProvider idProvider;
  private final String id;

  private EventLoopGroup clientThreads;

  // TODO: use a builder
  private int workerThreadCount = 2;

  private final AtomicLong allRequestsSent = new AtomicLong();
  private final AtomicLong allResponsesReceived = new AtomicLong();

  // TODO: add support for local server
  public TinyTCPClient(final IdProvider idProvider) {
    Objects.requireNonNull(idProvider, "idProvider cannot be null");
    this.idProvider = idProvider;
    this.id = idProvider.id();
    running.set(true);
    logger.info("Started client[{}]", id);
  }

  public void establishConnections(final List<ServerDescriptor> serverDescriptors) {
    if (!running.get()) {
      logger.info("Cannot establish connections with a stopped client[{}]", id);
      return;
    }
    if (writeLock.tryLock()) {
      try {
        if (serverDescriptors != null && !serverDescriptors.isEmpty()) {
          for (final ServerDescriptor serverDescriptor : serverDescriptors) {
            establishConnection(serverDescriptor);
          }
        }
      } finally {
        writeLock.unlock();
      }
    }
  }

  // do not mess with the lifecycle
  public boolean establishConnection(final ServerDescriptor serverDescriptor) {
    boolean success = false;
    if (!running.get()) {
      logger.info("Cannot establish connections with a stopped client[{}]", id);
      return success;
    }
    Objects.requireNonNull(serverDescriptor, "serverDescriptor cannot be null");
    if (trackedServers.containsKey(serverDescriptor)) {
      logger.error(
          "{} is already in the list of connected servers. To re-add it, first drop, then add it back",
          serverDescriptor);
      return success;
    }
    if (writeLock.tryLock()) {
      try {
        final long startNanos = System.nanoTime();
        logger.info("Establishing client[{}] connection to {}", id, serverDescriptor);
        // TODO: local client
        final boolean localClient = serverDescriptor.connectLocally();
        InternalLoggerFactory.setDefaultFactory(Log4J2LoggerFactory.INSTANCE);
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
            socketChannel.pipeline().addLast(new TinyTCPStateHandler(id, Type.CLIENT));
          }
        });
        Channel clientChannel = null;
        try {
          clientChannel = clientBootstrap.connect(serverDescriptor.getAddress().getHostName(),
              serverDescriptor.getAddress().getPort()).sync().channel();
        } catch (InterruptedException problem) {
          logger.error("Failed to connect to " + serverDescriptor, problem);
          return success;
        }

        final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        if (isChannelHealthy(clientChannel)) {
          trackedServers.put(serverDescriptor, clientChannel);
          success = true;
          logger.info("Established client[{}] connection to {} in {} millis", id,
              clientChannel.remoteAddress(), elapsedMillis);
        } else {
          logger.info("Failed to establish client[{}] connection to {} in {} millis", id,
              serverDescriptor, elapsedMillis);
        }
      } finally {
        writeLock.unlock();
      }
    }
    return success;
  }

  public boolean isConnectionEstablished(final ServerDescriptor serverDescriptor) {
    final Channel channel = trackedServers.get(serverDescriptor);
    return isChannelHealthy(channel);
  }

  public boolean severeConnection(final ServerDescriptor serverDescriptor) {
    boolean success = false;
    if (!running.get()) {
      logger.info("Cannot severe connections for a stopped client[{}]", id);
      return success;
    }
    if (writeLock.tryLock()) {
      try {
        if (trackedServers.containsKey(serverDescriptor)) {
          final long startNanos = System.nanoTime();
          try {
            final Channel clientChannel = trackedServers.get(serverDescriptor);
            logger.info(
                "Severing client[{}] connection to {} [allRequestsSent:{},allResponsesReceived:{}]",
                id, clientChannel.remoteAddress(), allRequestsSent.get(),
                allResponsesReceived.get());
            if (clientChannel != null) {
              clientChannel.close().await();
            }
            if (clientThreads != null) {
              clientThreads.shutdownGracefully().await();
            }
            if (clientChannel != null) {
              clientChannel.closeFuture().await().await();
            }
            trackedServers.remove(serverDescriptor);
            success = true;
            final long elapsedMillis =
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            logger.info("Severed client[{}] connection to {} in {} millis", id,
                clientChannel.remoteAddress(), elapsedMillis);
          } catch (InterruptedException problem) {
            logger.error("Encountered a problem while severing connection to " + serverDescriptor,
                problem);
          }
        } else {
          logger.error("{} does not have an established connection that can be severed",
              serverDescriptor);
        }
      } finally {
        writeLock.unlock();
      }
    }
    return success;
  }

  public void severeConnections(final List<ServerDescriptor> serverDescriptors) {
    if (!running.get()) {
      logger.info("Cannot severe connections for a stopped client[{}]", id);
      return;
    }
    if (serverDescriptors != null && !serverDescriptors.isEmpty()) {
      for (final ServerDescriptor serverDescriptor : Collections
          .unmodifiableList(serverDescriptors)) {
        severeConnection(serverDescriptor);
      }
    }
  }

  // no messing with the lifecycle
  public void stop() {
    // logger.info("Stopping client[{}]", id);
    if (!running.get()) {
      logger.info("Cannot stop an already stopped client[{}]", id);
    }
    for (final ServerDescriptor serverDescriptor : trackedServers.keySet()) {
      severeConnection(serverDescriptor);
    }
    running.set(false);
    logger.info("Stopped client[{}]", id);
  }

  private static boolean isChannelHealthy(final Channel channel) {
    return channel != null && channel.isOpen() && channel.isActive();
  }

  public boolean isRunning() {
    return running.get();
  }

  public String getId() {
    return id;
  }

  // TODO: externalize
  public boolean sendToServer(final ServerDescriptor serverDescriptor, final Request request) {
    if (!running.get()) {
      logger.error("Cannot pipe a request down a stopped client");
      return false;
    }
    final Channel clientChannel = trackedServers.get(serverDescriptor);
    if (!isChannelHealthy(clientChannel)) {
      logger.error("Cannot pipe a request down a stopped channel");
      return false;
    }
    allRequestsSent.incrementAndGet();
    logger.info("Client[{}] sending to server {}", id, request);
    final byte[] serializedRequest = request.serialize();
    final ChannelFuture future = clientChannel.write(Unpooled.copiedBuffer(serializedRequest));
    future.addListener(new ChannelFutureListener() {

      @Override
      public void operationComplete(final ChannelFuture future) throws Exception {
        if (future.isSuccess()) {
          logger.info("Client[{}] write succeeded", id);
          if (logger.isDebugEnabled()) {
            logger.debug("Client[{}] write succeeded", id);
          }
        } else {
          logger.error(String.format("Client[%s] encountered error", id), future.cause());
          future.cause().printStackTrace();
        }
      }
    });
    future.awaitUninterruptibly();
    return future.isDone();
  }

  final class FlusherDaemon extends Thread {
    private final long sleepMillis;

    FlusherDaemon(final long sleepMillis) {
      setName("request-flusher");
      setDaemon(true);
      this.sleepMillis = sleepMillis;
    }

    @Override
    public void run() {
      while (!interrupted()) {
        if (readLock.tryLock()) {
          try {
            for (final Map.Entry<ServerDescriptor, Channel> trackedServer : trackedServers
                .entrySet()) {
              try {
                final Channel channel = trackedServer.getValue();
                final ServerDescriptor server = trackedServer.getKey();
                if (channel.isWritable()) {
                  // logger.info("Client[{}] flushing {} requests to {}", id, pendingRequests.get(),
                  // server);
                  channel.flush();
                }
              } catch (Exception problem) {
                logger.error("Encountered problem while flushing channel to server", problem);
              }
            }
          } finally {
            readLock.unlock();
          }
        }
        try {
          sleep(sleepMillis);
        } catch (InterruptedException e) {
          interrupt();
        }
      }
    }
  };

  // TODO: externalize
  public void handleServerResponse(final Response response) {
    logger.info("Client[{}] received from server {}", id, response);
  }

  /**
   * Handler for processing client-side I/O events.
   * 
   * @author gaurav
   */
  public class TinyTCPClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    @Override
    public void channelRead0(final ChannelHandlerContext channelHandlerContext,
        final ByteBuf payload) {
      allResponsesReceived.incrementAndGet();
      final byte[] responseBytes = ByteBufUtil.getBytes(payload);
      final Response response =
          new TinyResponse(idProvider, Optional.empty()).deserialize(responseBytes);
      handleServerResponse(response);
    }

  }

}
