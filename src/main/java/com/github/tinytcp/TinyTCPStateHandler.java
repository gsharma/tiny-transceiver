package com.github.tinytcp;

import java.net.SocketAddress;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.PendingWriteQueue;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;

/**
 * Handle all state transitions for tiny tcp transceivers.
 * 
 * @author gaurav
 */
final class TinyTCPStateHandler extends ChannelDuplexHandler {
  private static final Logger logger =
      LogManager.getLogger(TinyTCPStateHandler.class.getSimpleName());

  private final AtomicInteger currentActiveConnections = new AtomicInteger();
  private final AtomicLong allAcceptedConnections = new AtomicLong();
  private final AtomicLong allConnectionIdleTimeouts = new AtomicLong();
  private final AtomicLong allRequestsHandled = new AtomicLong();

  private PendingWriteQueue pendingWriteQueue;
  private final String id;
  private final Type type;
  private final IdProvider idProvider;

  enum Type {
    CLIENT("client"), SERVER("server");
    private String name;

    private Type(String name) {
      this.name = name;
    }
  }

  // IdProvider is pushed down for any requests that might need to be curated
  TinyTCPStateHandler(final String parentId, final Type type, final IdProvider idProvider) {
    this.id = parentId;
    this.type = type;
    this.idProvider = idProvider;
    // flusherDaemon = new FlusherDaemon(200L);
    // flusherDaemon.start();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
    logger.error(String.format("%s[%s] Error", type.name, id), cause);
    context.write(Unpooled.copiedBuffer("Tiny TCP Transceiver Error", CharsetUtil.UTF_8))
        .addListener(ChannelFutureListener.CLOSE);
  }

  @Override
  public void bind(final ChannelHandlerContext context, final SocketAddress localAddress,
      final ChannelPromise promise) {
    context.bind(localAddress, promise.addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) throws Exception {
        if (!future.isSuccess()) {
          logger.error("{}[{}] failed to bind to {}", type.name, id, localAddress);
          context.write(Unpooled.copiedBuffer("Tiny TCP Transceiver Error", CharsetUtil.UTF_8))
              .addListener(ChannelFutureListener.CLOSE);
        } else {
          logger.info("{}[{}] bound to {}", type.name, id, localAddress);
        }
      }
    }));
  }

  @Override
  public void connect(final ChannelHandlerContext context, final SocketAddress remoteAddress,
      final SocketAddress localAddress, final ChannelPromise promise) {
    context.connect(remoteAddress, localAddress, promise.addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) {
        if (!future.isSuccess()) {
          logger.error("{}[{}] failed to connect to {}", type.name, id, remoteAddress);
          context.write(Unpooled.copiedBuffer("Tiny TCP Transceiver Error", CharsetUtil.UTF_8))
              .addListener(ChannelFutureListener.CLOSE);
        } else {
          logger.info("{}[{}] connected to {}", type.name, id, remoteAddress);
        }
      }
    }));
  }

  @Override
  public void write(final ChannelHandlerContext context, final Object message,
      final ChannelPromise promise) {
    logger.info("{}[{}] writing", type.name, id);
    pendingWriteQueue.add(message, promise);
    // pendingRequests.set(pendingWriteQueue.size());
    drain(context);
    /*
     * context.write(message, promise.addListener(new ChannelFutureListener() {
     * 
     * @Override public void operationComplete(ChannelFuture future) { if (!future.isSuccess()) {
     * logger.error("{}[{}] write failed", type.name, id);
     * context.write(Unpooled.copiedBuffer("Tiny TCP Transceiver Error", CharsetUtil.UTF_8))
     * .addListener(ChannelFutureListener.CLOSE); } } }));
     */
  }

  @Override
  public void channelReadComplete(final ChannelHandlerContext context) {
    // pendingRequests.set(pendingWriteQueue.size());
    logger.info("{}[{}] read complete", type.name, id);
    allRequestsHandled.incrementAndGet();
    drain(context);
    // context.flush();
  }

  @Override
  public void channelInactive(final ChannelHandlerContext channelHandlerContext) {
    // pendingRequests.set(pendingWriteQueue.size());
    currentActiveConnections.decrementAndGet();
    pendingWriteQueue.removeAndWriteAll();
    logger.info("{}[{}] is inactive", type.name, id);
  }

  @Override
  public void channelActive(final ChannelHandlerContext channelHandlerContext) {
    allAcceptedConnections.incrementAndGet();
    currentActiveConnections.incrementAndGet();
    logger.info("{}[{}] is active", type.name, id);
  }

  @Override
  public void channelRegistered(final ChannelHandlerContext channelHandlerContext) {
    logger.info("{}[{}] is registered", type.name, id);
    pendingWriteQueue = new PendingWriteQueue(channelHandlerContext);
  }

  @Override
  public void channelUnregistered(final ChannelHandlerContext channelHandlerContext) {
    pendingWriteQueue.removeAndWriteAll();
    logger.info(
        "{}[{}] is unregistered [allAcceptedConnections:{},allIdleTimeouts:{},allRequestsHandled:{}]",
        type.name, id, allAcceptedConnections.get(), allConnectionIdleTimeouts.get(),
        allRequestsHandled.get());
  }

  @Override
  public void userEventTriggered(final ChannelHandlerContext context, final Object event) {
    if (event instanceof IdleStateEvent) {
      allConnectionIdleTimeouts.incrementAndGet();
      final IdleStateEvent idleStateEvent = (IdleStateEvent) event;
      switch (idleStateEvent.state()) {
        case READER_IDLE:
          logger.info("{}[{}] reader is idle", type.name, id);
          publishHeartbeat(context);
          // context.disconnect();
          break;
        case WRITER_IDLE:
          logger.info("{}[{}] writer is idle", type.name, id);
          publishHeartbeat(context);
          break;
        case ALL_IDLE:
          logger.info("{}[{}] reader & writer are idle", type.name, id);
          publishHeartbeat(context);
          break;
        default:
          logger.info("{}[{}] in {} idle state", type.name, id, idleStateEvent.state());
          break;
      }
    }
  }

  private void publishHeartbeat(final ChannelHandlerContext context) {
    logger.info("{}[{}] publishing heartbeat to overcome idle state", type.name, id);
    switch (type) {
      case SERVER:
        final Response response =
            new TinyResponse(idProvider, Optional.empty(), ExchangeType.HEARTBEAT);
        final byte[] serializedResponse = response.serialize();
        context.write(Unpooled.copiedBuffer(serializedResponse));
        break;
      case CLIENT:
        final Request request = new TinyRequest(idProvider, ExchangeType.HEARTBEAT);
        final byte[] serializedRequest = request.serialize();
        context.write(Unpooled.copiedBuffer(serializedRequest));
        break;
    }
  }

  @Override
  public void channelWritabilityChanged(final ChannelHandlerContext context) {
    logger.info("{}[{}] channel writability changed", type.name, id);
  }

  @Override
  public void flush(final ChannelHandlerContext context) {
    drain(context);
  }

  private void drain(final ChannelHandlerContext context) {
    if (pendingWriteQueue != null && context.executor().inEventLoop()) {
      final int pendingMessages = pendingWriteQueue.size();
      if (pendingWriteQueue.removeAndWrite() == null) {
        // logger.info("{}[{}] channel draining {} messages", type.name, id, pendingMessages);
        context.flush();
      } else {
        logger.info("{}[{}] channel draining {} messages", type.name, id, pendingMessages);
        context.flush();
      }
    }
  }

}
