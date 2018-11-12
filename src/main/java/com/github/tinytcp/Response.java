package com.github.tinytcp;

/**
 * Base response for the transceiver; its path is client<-server.
 * 
 * @author gaurav
 */
public interface Response {

  // Read the unique id of this response
  String getId();

  // Return error, if any
  String getError();

  // Flatten the request to a byte[]
  byte[] serialize();

  // Deserialize and reconstruct the response object. Ideally, this should be a static
  // function not requiring the call to default response implementation constructor.
  Response deserialize(byte[] flattenedResponse);

  // Report the IdProvider
  // IdProvider getIdProvider();

  String getRequestId();

  ExchangeType getType();

}
