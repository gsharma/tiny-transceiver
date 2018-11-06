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
  private final AtomicLong allRequestsServicedCount;

  public TinyTCPServerHandler(final AtomicLong allRequestsServicedCount) {
    this.allRequestsServicedCount = allRequestsServicedCount;
  }

  @Override
  public void channelRead(ChannelHandlerContext context, Object msg) throws Exception {
    allRequestsServicedCount.incrementAndGet();
    logger.info("Server received type:" + msg.getClass().getName());
    final ByteBuf payload = (ByteBuf) msg;
    final String received = payload.toString(CharsetUtil.UTF_8);
    logger.info("Server received: " + received);

    final String response = handle(received);

    logger.info("Server sending: " + response);
    context.writeAndFlush(Unpooled.copiedBuffer(response, CharsetUtil.UTF_8))
        .addListener(ChannelFutureListener.CLOSE);
    // context.write(Unpooled.copiedBuffer(response, CharsetUtil.UTF_8));
  }

  // Charset.UTF_8
  public String handle(final String payload) {
    return "Yo " + payload;
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext context) throws Exception {
    logger.info("Server channel read complete");
    context.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
    logger.error(cause);
    context.close();
  }

}
