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
  private static final AtomicLong servicedRequests = new AtomicLong();

  @Override
  public void channelRead(ChannelHandlerContext context, Object msg) throws Exception {
    servicedRequests.incrementAndGet();
    logger.info("Server received type:" + msg.getClass().getName());
    final ByteBuf payload = (ByteBuf) msg;
    final String received = payload.toString(CharsetUtil.UTF_8);
    logger.info("Server received: " + received);

    final String response = "Yo " + received;
    logger.info("Server sending: " + response);
    context.write(Unpooled.copiedBuffer(response, CharsetUtil.UTF_8));
    logger.info("Total server requests processed:" + servicedRequests.get());
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext context) throws Exception {
    context.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
    cause.printStackTrace();
    context.close();
  }

}
