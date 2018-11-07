package com.github.tinytcp;

import io.netty.buffer.ByteBuf;

/**
 * Base request for the transceiver; its path is client->server.
 * 
 * @author gaurav
 */
public interface Request {
  String getId();

  ByteBuf serialize();

  Request deserialize();

}
