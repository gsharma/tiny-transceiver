package com.github.tinytcp;

/**
 * Base request for the transceiver; its path is client->server.
 * 
 * @author gaurav
 */
public interface Request {

  // Read the unique id of this request
  String getId();

  // Flatten the request to a byte[]
  byte[] serialize();

  // Deserialize and reconstruct the request object. Ideally, this should be a static
  // function not requiring the call to default request implementation constructor.
  Request deserialize(byte[] flattenedRequest);

  // Report the IdProvider
  IdProvider getIdProvider();

}
