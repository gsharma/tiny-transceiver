package com.github.tinytcp;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;

/**
 * Write all the server-side business-logic here.
 * 
 * @author gaurav
 */
public class TinyTCPServerHandler extends ChannelInboundHandlerAdapter {
  private static final Logger logger =
      LogManager.getLogger(TinyTCPServerHandler.class.getSimpleName());
  private final String id;
  private final AtomicLong allRequestsServicedCount;

  public TinyTCPServerHandler(final String id, final AtomicLong allRequestsServicedCount) {
    this.id = id;
    this.allRequestsServicedCount = allRequestsServicedCount;
  }

  @Override
  public void channelRead(final ChannelHandlerContext context, final Object msg) throws Exception {
    allRequestsServicedCount.incrementAndGet();
    logger.info("Server [{}] received type {}", id, msg.getClass().getName());
    final ByteBuf payload = (ByteBuf) msg;
    final String received = payload.toString(CharsetUtil.UTF_8);
    logger.info("Server [{}] received {}", id, received);

    final String response = respondToClient(received);

    context.writeAndFlush(Unpooled.copiedBuffer(response, CharsetUtil.UTF_8))
        .addListener(ChannelFutureListener.CLOSE);
    // context.write(Unpooled.copiedBuffer(response, CharsetUtil.UTF_8));
  }

  // Charset.UTF_8
  public String respondToClient(final String payload) {
    logger.info("Server [{}] responding to client, response: {}", id, payload);
    return "Yo " + payload;
  }

  @Override
  public void channelReadComplete(final ChannelHandlerContext context) throws Exception {
    logger.info("Server [{}] channel read complete", id);
    context.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause)
      throws Exception {
    logger.error(cause);
    context.close();
  }

}
