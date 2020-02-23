package com.github.tinytcp;

import java.lang.Thread.UncaughtExceptionHandler;
import java.net.SocketAddress;
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
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4J2LoggerFactory;

/**
 * A minimal TCP Transceiver that cuts through all the nonsense and means business.
 * 
 * @author gaurav
 */
public abstract class TinyTCPTransceiver implements TinyTransceiver {
  private static final Logger logger =
      LogManager.getLogger(TinyTCPTransceiver.class.getSimpleName());
  private final String serverId;
  private final IdProvider idProvider;

  private Channel serverChannel;
  private EventLoopGroup eventLoopThreads;
  private EventLoopGroup workerThreads;
  private final AtomicBoolean serverRunning = new AtomicBoolean();

  // TODO: use a builder
  private int eventLoopThreadCount = 1;
  private int workerThreadCount = 4;
  private ServerDescriptor serverDescriptor;

  protected ResponseHandler responseHandler;

  // private final AtomicInteger currentActiveConnections = new AtomicInteger();
  // private final AtomicLong allAcceptedConnections = new AtomicLong();
  // private final AtomicLong allConnectionIdleTimeouts = new AtomicLong();

  private final AtomicLong allServerRequests = new AtomicLong();
  private final AtomicLong allServerResponses = new AtomicLong();

  private final ReentrantReadWriteLock superLock = new ReentrantReadWriteLock(true);
  private final WriteLock writeLock = superLock.writeLock();
  private final ReadLock readLock = superLock.readLock();

  private final AtomicBoolean clientRunning = new AtomicBoolean();
  private final ConcurrentMap<ServerDescriptor, Channel> trackedServers = new ConcurrentHashMap<>();
  private final String clientId;

  private EventLoopGroup clientThreads;

  // private TinyTCPStateHandler serverStateHandler;
  // private TinyTCPStateHandler clientStateHandler;

  // TODO: use a builder
  private final AtomicLong allRequestsSent = new AtomicLong();
  private final AtomicLong allResponsesReceived = new AtomicLong();

  // **********************************************************
  // implementers should implement at least these first 2
  //
  public abstract Response serviceRequest(Request request);

  public abstract boolean dispatchRequest(final Request request,
      final ServerDescriptor serverDescriptor);

  @Override
  public void registerResponseHandler(final ResponseHandler responseHandler) {
    this.responseHandler = responseHandler;
  }
  //
  // **********************************************************

  protected TinyTCPTransceiver(final IdProvider idProvider) {
    Objects.requireNonNull(idProvider, "idProvider cannot be null");
    this.idProvider = idProvider;
    this.serverId = idProvider.id();
    this.clientId = idProvider.id();
    ResourceLeakDetector.setLevel(Level.SIMPLE);
  }

  // just don't mess with the lifecycle methods
  @Override
  public synchronized void startServer(final ServerDescriptor serverDescriptor) {
    final long startNanos = System.nanoTime();
    logger.info("Starting server[{}] at {}", serverId, serverDescriptor);
    Objects.requireNonNull(serverDescriptor, "serverDescriptor cannot be null");
    this.serverDescriptor = serverDescriptor;

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
    // serverStateHandler = new TinyTCPStateHandler(serverId, Type.SERVER);
    InternalLoggerFactory.setDefaultFactory(Log4J2LoggerFactory.INSTANCE);
    final ServerBootstrap serverBootstrap = new ServerBootstrap();
    serverBootstrap.group(eventLoopThreads, workerThreads).channel(NioServerSocketChannel.class)
        .handler(new LoggingHandler(LogLevel.INFO)).childHandler(new ChannelInitializer<Channel>() {
          @Override
          public void initChannel(final Channel channel) throws Exception {
            final ChannelPipeline pipeline = channel.pipeline();

            // final ConnectionMetricHandler connectionMetricHandler = new ConnectionMetricHandler(
            // currentActiveConnections, allAcceptedConnections, allConnectionIdleTimeouts);
            pipeline.addLast(new IdleStateHandler(60, 60, 0));
            // pipeline.addLast(connectionMetricHandler);
            pipeline.addLast(new ReadTimeoutHandler(120L, TimeUnit.SECONDS));
            pipeline.addLast(new WriteTimeoutHandler(120L, TimeUnit.SECONDS));
            pipeline.addLast(new TinyTCPServerHandler());
            pipeline.addLast(new TinyTCPStateHandler(serverId, Type.SERVER, idProvider));
          }
        });
    serverBootstrap.childOption(ChannelOption.TCP_NODELAY, true);
    serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
    serverBootstrap.childOption(ChannelOption.SO_REUSEADDR, true);
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
      serverRunning.set(true);
      // new ServerSentinel(5L, serverChannel).start();
      logger.info("Started server[{}] at {} in {} millis", serverId, serverChannel.localAddress(),
          elapsedMillis);
    } else {
      logger.info("Failed to start server[{}] at {} in {} millis", serverId, serverDescriptor,
          elapsedMillis);
    }
  }

  // just don't mess with the lifecycle methods
  @Override
  public synchronized void stopServer() {
    final long startNanos = System.nanoTime();
    if (!serverRunning.get()) {
      logger.info("Cannot stop an already stopped server[{}]", serverId);
    }
    logger.info("Stopping server[{}] at {}", serverId, serverChannel.localAddress());
    try {
      if (serverChannel != null) {
        serverChannel.disconnect();
        serverChannel.close().await();
      }
      if (eventLoopThreads != null) {
        eventLoopThreads.shutdownGracefully(100L, 200L, TimeUnit.MILLISECONDS).await();
      }
      if (workerThreads != null) {
        workerThreads.shutdownGracefully(100L, 200L, TimeUnit.MILLISECONDS).await();
      }
      if (serverChannel != null) {
        serverChannel.closeFuture().await();
      }
    } catch (InterruptedException problem) {
      logger.error("Encountered a problem while stopping server at " + serverChannel.localAddress(),
          problem);
    }
    serverRunning.set(false);
    final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    logger.info("Stopped server[{}] at {} in {} millis", serverId, serverDescriptor, elapsedMillis);
  }

  @Override
  public boolean isServerRunning() {
    return serverRunning.get();
  }

  @Override
  public String getServerId() {
    return serverId;
  }

  @Override
  public long getAllServerRequests() {
    return allServerRequests.get();
  }

  @Override
  public long getAllServerResponses() {
    return allServerResponses.get();
  }

  protected Response serviceRequestInternal(final Request request) {
    final Response response =
        new TinyResponse(idProvider, Optional.ofNullable(request.getId()), ExchangeType.NORMAL);
    return response;
  }

  /**
   * Figure the server-side business-logic here.
   * 
   * @author gaurav
   */
  final class TinyTCPServerHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(final ChannelHandlerContext context, final Object msg) {
      final long startNanos = System.nanoTime();
      allServerRequests.incrementAndGet();
      final ByteBuf payload = (ByteBuf) msg;
      final byte[] requestBytes = ByteBufUtil.getBytes(payload);
      final Request request =
          new TinyRequest(idProvider, ExchangeType.NORMAL).deserialize(requestBytes);
      final SocketAddress client = context.channel().remoteAddress();
      // msg.getClass().getName();
      logger.info("Server[{}] received {} from {}", serverId, request, client);

      // server-side business logic holder
      final Response response = serviceRequest(request);

      final byte[] serializedResponse = response.serialize();

      allServerResponses.incrementAndGet();
      final long elapsedMicros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startNanos);
      logger.info("Server[{}] responded to client {} with {} in {} micros", serverId, client,
          response, elapsedMicros);
      final ChannelFuture future = context.write(Unpooled.copiedBuffer(serializedResponse));
      future.addListener(new ChannelFutureListener() {

        @Override
        public void operationComplete(final ChannelFuture future) throws Exception {
          if (future.isSuccess()) {
            logger.info("Server[{}] write succeeded", serverId);
            if (logger.isDebugEnabled()) {
              logger.debug("Server[{}] write succeeded", serverId);
            }
          } else {
            logger.error(String.format("Server[%s] encountered error", serverId), future.cause());
            future.cause().printStackTrace();
          }
        }
      });
      return;
    }
  }

  /**
   * A minimal TCP Client that cuts through all the nonsense and means business.
   * 
   * @author gaurav
   */
  // TODO: add support for local server

  @Override
  public synchronized void startClient() {
    clientRunning.set(true);
    logger.info("Started client[{}]", clientId);
  }

  public void connectToServers(final List<ServerDescriptor> serverDescriptors) {
    if (!clientRunning.get()) {
      logger.info("Cannot establish connections with a stopped client[{}]", clientId);
      return;
    }
    if (writeLock.tryLock()) {
      try {
        if (serverDescriptors != null && !serverDescriptors.isEmpty()) {
          for (final ServerDescriptor serverDescriptor : serverDescriptors) {
            connectToServer(serverDescriptor);
          }
        }
      } finally {
        writeLock.unlock();
      }
    }
  }

  // do not mess with the lifecycle
  @Override
  public boolean connectToServer(final ServerDescriptor serverDescriptor) {
    boolean success = false;
    if (!clientRunning.get()) {
      logger.info("Cannot establish connections with a stopped client[{}]", clientId);
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
        logger.info("Establishing client[{}] connection to {}", clientId, serverDescriptor);
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
        // clientStateHandler = new TinyTCPStateHandler(clientId, Type.CLIENT);
        clientBootstrap.group(clientThreads).channel(NioSocketChannel.class);
        clientBootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        clientBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
        clientBootstrap.handler(new LoggingHandler(LogLevel.INFO));
        clientBootstrap.handler(new ChannelInitializer<SocketChannel>() {
          protected void initChannel(SocketChannel socketChannel) throws Exception {
            socketChannel.pipeline().addLast(new TinyTCPClientHandler());
            socketChannel.pipeline()
                .addLast(new TinyTCPStateHandler(clientId, Type.CLIENT, idProvider));
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
          logger.info("Established client[{}] connection to {} in {} millis", clientId,
              clientChannel.remoteAddress(), elapsedMillis);
        } else {
          logger.info("Failed to establish client[{}] connection to {} in {} millis", clientId,
              serverDescriptor, elapsedMillis);
        }
      } finally {
        writeLock.unlock();
      }
    }
    return success;
  }

  @Override
  public boolean severeConnection(final ServerDescriptor serverDescriptor) {
    boolean success = false;
    if (!clientRunning.get()) {
      logger.info("Cannot severe connections for a stopped client[{}]", clientId);
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
                clientId, clientChannel.remoteAddress(), allRequestsSent.get(),
                allResponsesReceived.get());
            if (clientChannel != null) {
              clientChannel.disconnect();
              clientChannel.close().await();
            }
            if (clientThreads != null) {
              clientThreads.shutdownGracefully(100L, 200L, TimeUnit.MILLISECONDS).await();
            }
            // if (clientChannel != null) {
            // clientChannel.closeFuture().await().await();
            // }
            trackedServers.remove(serverDescriptor);
            success = true;
            final long elapsedMillis =
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            logger.info("Severed client[{}] connection to {} in {} millis", clientId,
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
    if (!clientRunning.get()) {
      logger.info("Cannot severe connections for a stopped client[{}]", clientId);
      return;
    }
    if (serverDescriptors != null && !serverDescriptors.isEmpty()) {
      for (final ServerDescriptor serverDescriptor : Collections
          .unmodifiableList(serverDescriptors)) {
        severeConnection(serverDescriptor);
      }
    }
  }

  @Override
  public boolean isConnected(final ServerDescriptor serverDescriptor) {
    boolean connected = false;
    if (!clientRunning.get()) {
      logger.info("Stopped client[{}] is disconnected from all servers", clientId);
      return connected;
    }
    if (readLock.tryLock()) {
      try {
        final Channel channel = trackedServers.get(serverDescriptor);
        connected = isChannelHealthy(channel);
      } finally {
        readLock.unlock();
      }
    }
    return connected;
  }

  // no messing with the lifecycle
  @Override
  public synchronized void stopClient() {
    final long startNanos = System.nanoTime();
    // logger.info("Stopping client[{}]", id);
    if (!clientRunning.get()) {
      logger.info("Cannot stop an already stopped client[{}]", clientId);
    }
    logger.info("Stopping client[{}]", clientId);
    for (final ServerDescriptor serverDescriptor : trackedServers.keySet()) {
      severeConnection(serverDescriptor);
    }
    clientRunning.set(false);
    final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    logger.info("Stopped client[{}] in {} millis", clientId, elapsedMillis);
  }

  protected boolean isChannelHealthy(final Channel channel) {
    return channel != null && channel.isOpen() && channel.isActive();
  }

  @Override
  public boolean isClientRunning() {
    return clientRunning.get();
  }

  @Override
  public String getClientId() {
    return clientId;
  }

  protected boolean dispatchRequestInternal(final Request request,
      final ServerDescriptor serverDescriptor) {
    if (!clientRunning.get()) {
      logger.error("Cannot pipe a request down a stopped client");
      return false;
    }
    final Channel clientChannel = trackedServers.get(serverDescriptor);
    if (!isChannelHealthy(clientChannel)) {
      logger.error("Cannot pipe a request down a stopped channel");
      return false;
    }
    allRequestsSent.incrementAndGet();
    logger.info("Client[{}] sending to server {}", clientId, request);
    final byte[] serializedRequest = request.serialize();
    final ChannelFuture future = clientChannel.write(Unpooled.copiedBuffer(serializedRequest));
    // logger.info("Client[{}] sent to server {}", clientId, request);
    future.addListener(new ChannelFutureListener() {

      @Override
      public void operationComplete(final ChannelFuture future) throws Exception {
        if (future.isSuccess()) {
          logger.info("Client[{}] write succeeded", clientId);
          if (logger.isDebugEnabled()) {
            logger.debug("Client[{}] write succeeded", clientId);
          }
        } else {
          logger.error(String.format("Client[%s] encountered error", clientId), future.cause());
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
                  // logger.info("Client[{}] flushing {} requests to {}", id,
                  // pendingRequests.get(),
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

  /**
   * Handler for processing client-side I/O events.
   * 
   * @author gaurav
   */
  final class TinyTCPClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    @Override
    public void channelRead0(final ChannelHandlerContext channelHandlerContext,
        final ByteBuf payload) {
      allResponsesReceived.incrementAndGet();
      final byte[] responseBytes = ByteBufUtil.getBytes(payload);
      final Response response = new TinyResponse(idProvider, Optional.empty(), ExchangeType.NORMAL)
          .deserialize(responseBytes);
      responseHandler.handleResponse(response);
    }

  }

}

