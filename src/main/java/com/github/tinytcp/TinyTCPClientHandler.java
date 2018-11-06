package com.github.tinytcp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;

/**
 * Write all the client-side business logic here.
 * 
 * @author gaurav
 */
public class TinyTCPClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
  private static final Logger logger =
      LogManager.getLogger(TinyTCPClientHandler.class.getSimpleName());

  @Override
  public void channelActive(final ChannelHandlerContext channelHandlerContext) {
    final String payload = "Yo!";
    logger.info("Client sending " + payload);
    channelHandlerContext.writeAndFlush(Unpooled.copiedBuffer(payload, CharsetUtil.UTF_8));
  }

  @Override
  public void channelRead0(final ChannelHandlerContext channelHandlerContext,
      final ByteBuf payload) {
    logger.info("Client received: " + payload.toString(CharsetUtil.UTF_8));
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext channelHandlerContext,
      final Throwable cause) {
    logger.error(cause);
    channelHandlerContext.close();
  }

}
