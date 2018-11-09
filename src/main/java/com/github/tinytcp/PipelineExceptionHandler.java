package com.github.tinytcp;

import java.net.SocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.CharsetUtil;

/**
 * Handle all uncaught exceptions
 * 
 * @author gaurav
 */
public class PipelineExceptionHandler extends ChannelDuplexHandler {
  private static final Logger logger =
      LogManager.getLogger(PipelineExceptionHandler.class.getSimpleName());

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
    logger.error(cause);
    context.write(Unpooled.copiedBuffer("Tiny TCP Server Error", CharsetUtil.UTF_8))
        .addListener(ChannelFutureListener.CLOSE);
  }

  @Override
  public void connect(ChannelHandlerContext context, SocketAddress remoteAddress,
      SocketAddress localAddress, ChannelPromise promise) {
    context.connect(remoteAddress, localAddress, promise.addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) {
        if (!future.isSuccess()) {
          context.write(Unpooled.copiedBuffer("Tiny TCP Server Error", CharsetUtil.UTF_8))
              .addListener(ChannelFutureListener.CLOSE);
        }
      }
    }));
  }

  @Override
  public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
    context.write(message, promise.addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) {
        if (!future.isSuccess()) {
          context.write(Unpooled.copiedBuffer("Tiny TCP Server Error", CharsetUtil.UTF_8))
              .addListener(ChannelFutureListener.CLOSE);
        }
      }
    }));
  }

  @Override
  public void channelReadComplete(final ChannelHandlerContext context) {
    context.flush();
  }

}
