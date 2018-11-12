package com.github.tinytcp;

/**
 * This is just a tiny transceiver.
 * 
 * @author gaurav
 */
public interface TinyTransceiver {

  // core operations
  public Response serviceRequest(final Request request);

  public boolean dispatchRequest(final Request request, final ServerDescriptor server);

  public void registerResponseHandler(final ResponseHandler responseHandler);

  interface ResponseHandler {
    public void handleResponse(final Response response);
  }


  // lifecycle operations
  public void startServer(final ServerDescriptor serverDescriptor);

  public void startClient();

  public boolean connectToServer(final ServerDescriptor serverDescriptor);

  public boolean isConnected(final ServerDescriptor serverDescriptor);

  public boolean severeConnection(final ServerDescriptor serverDescriptor);

  public void stopServer();

  public String getServerId();

  public boolean isServerRunning();

  public void stopClient();

  public String getClientId();

  public boolean isClientRunning();


  // metrics - needs more work
  long getAllServerRequests();

  long getAllServerResponses();

}
