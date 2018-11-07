package com.github.tinytcp;

import io.netty.buffer.ByteBuf;

/**
 * Base response for the transceiver; its path is client<-server.
 * 
 * @author gaurav
 */
public interface Response {
  String getId();

  ByteBuf serialize();

  Response deserialize();

}
