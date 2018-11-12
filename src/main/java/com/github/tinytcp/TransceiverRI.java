package com.github.tinytcp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This is a super-simple Reference Implementation (RI) of a Transceiver to give a sense of how easy
 * it is to create a custom Transceiver.
 * 
 * Basic steps are:<br/>
 * 0. pick an id provider<br/>
 * 1. create a server<br/>
 * 2. start the server<br/>
 * 3. create a client<br/>
 * 4. start the client<br/>
 * 5. connect client to server<br/>
 * 6. start dispatching requests to server<br/>
 * 7. when done, severe connection<br/>
 * 8. shut everything down<br/>
 * 
 * Note that the same client can be used to connect to many servers.
 * 
 * @author gaurav
 */
public class TransceiverRI extends TinyTCPTransceiver {
  private static final Logger logger = LogManager.getLogger(TransceiverRI.class.getSimpleName());

  /**
   * Select an idProvider of choice and inject it for all to use. Also, register a ResponseHandler
   * for the client to callback to when it receives a response back from the server.
   * 
   * @param idProvider
   */
  public TransceiverRI(final IdProvider idProvider) {
    super(idProvider);
    registerResponseHandler(new ClientSideResponseHandler());
  }

  /**
   * Connect to the server specified by the ServerDescriptor.
   */
  @Override
  public boolean connectToServer(final ServerDescriptor serverDescriptor) {
    return super.connectToServer(serverDescriptor);
  }

  /**
   * Dispatch the given request to the server specified by the ServerDescriptor. This should
   * obviously be preceded by establishing the server connection for the very first time.
   */
  @Override
  public boolean dispatchRequest(final Request request, final ServerDescriptor serverDescriptor) {
    return dispatchRequestInternal(request, serverDescriptor);
  }

  /**
   * Provide how the server should handle a request it has received from the client.
   */
  @Override
  public Response serviceRequest(final Request request) {
    return serviceRequestInternal(request);
  }

  /**
   * Severe connection to the server.
   */
  @Override
  public boolean severeConnection(final ServerDescriptor serverDescriptor) {
    return super.severeConnection(serverDescriptor);
  }

  /**
   * Just a simple ResponseHandler to tell the client how to handle a response it received back from
   * the server asynchronously.
   */
  public class ClientSideResponseHandler implements ResponseHandler {
    @Override
    public void handleResponse(Response response) {
      logger.info("Client[{}] received from server {}", getClientId(), response);
    }
  }

}
