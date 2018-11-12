[![Build Status](https://img.shields.io/travis/gsharma/tiny-transceiver/master.svg)](https://travis-ci.org/gsharma/tiny-transceiver)
[![Test Coverage](https://img.shields.io/codecov/c/github/gsharma/tiny-transceiver/master.svg)](https://codecov.io/github/gsharma/tiny-transceiver?branch=master)

# Tiny TCP Transceiver

## Background Motivation
I often run into situations where a small-footprint transceiver is needed and after a few repeat iterations of rolling my own, I'm tired and bored and want to put up a simple reusable library for use. The most important requirement for this library is that it should allow a very lazy usage with minimal amount of code needed to get up and running. That, and it needed to provide both the server and client implementations with the ability to fire them up, as needed - fire up a local server and a local client or fire up a local client that talks to n other remote servers - make other combinations, as needed. The other thing to keep in mind is that this is not an exercise in beautiful design or beautiful interfaces or or beautiful extensibility or beautiful code blah blah - it needs to be fast and need to work. that's all. Okay, with the motivation and disclaimer out of the way, let's see the API and brief usage guide. 


## API
 
### Core operations
```java
// this is the server-side handler that will have the server's logic
// to service and incoming request and return a response.
Response serviceRequest(final Request request);

// this is the client-side implementation to dispatch a request to a
// server specified by the server descriptor
boolean dispatchRequest(final Request request, final ServerDescriptor server);

// this allows a callback/handler to be registered on the client-side to
// handle the response received asynchronously from the server
void registerResponseHandler(final ResponseHandler responseHandler);

// provide a custom implementation for client-side server-response handling
interface ResponseHandler {
  public void handleResponse(final Response response);
}
```

### Lifecycle operations
```java
// start the server as specified by the given server descriptor - at
// the successful completion of this method, getServerId() should return 
// the server's id and isServerRunning() should return true;
void startServer(final ServerDescriptor serverDescriptor);

// start the client - completion of this should be similar to the server
// lifecycle - getClientId() should return client's id and isClientRunning()
// should return true
void startClient();

// establish a connection from the client to the provided server. Ideally,
// we'll already have the server up and running and listening on a socket.
// Note that this can be repeatedly called with different serverDescriptors
// to connect to many different servers from the same client.
boolean connectToServer(final ServerDescriptor serverDescriptor);

// returns the connection status from the client to server specified by
// the given serverDescriptor
boolean isConnected(final ServerDescriptor serverDescriptor);

// severe a client to server connection on-demand. Note that calling
// stopClient() will do that for all open and active connections.
boolean severeConnection(final ServerDescriptor serverDescriptor);

// try to gracefully stop the server.
void stopServer();

// report the immutable serverId.
String getServerId();

// report if the server is up and running.
boolean isServerRunning();

// try to gracefully stop the client.
void stopClient();

// report the immutable clientId.
String getClientId();

// report if the client is up and running.
boolean isClientRunning();
```
 
 
## Usage Guide
```java
 * Basic steps are:
 * 0. pick an id provider
 * 1. create a server
 * 2. start the server
 * 3. create a client
 * 4. start the client
 * 5. connect client to server
 * 6. start dispatching requests to server
 * 7. when done, severe connection
 * 8. shut everything down
 
// Fire up the server
final String serverHost = "localhost";
final int serverPort = 7500;
final ServerDescriptor serverDescriptor = new ServerDescriptor(serverHost, serverPort, false);
final TinyTransceiver server = new TransceiverRI(idProvider);
Thread serverThread = new Thread() {
  public void run() {
    server.startServer(serverDescriptor);
  }
};
serverThread.start();
int spinCounter = 0, spinsAllowed = 100;
long waitMillis = 100L;
while (!server.isServerRunning()) {
  spinCounter++;
  if (spinCounter > spinsAllowed) {
    logger.error("Failed to bootstrap server after {} spins", spinsAllowed);
    return;
  }
  logger.info("Waiting {} millis for server to bootstrap", waitMillis);
  Thread.sleep(waitMillis);
}
assertTrue(server.isServerRunning());

// Fire up the client and connect to server
final TinyTransceiver client = new TransceiverRI(idProvider);
client.startClient();
assertTrue(client.isClientRunning());
Thread clientThread = new Thread() {
  public void run() {
    assertTrue(client.connectToServer(serverDescriptor));
  }
};
clientThread.start();
waitMillis = 100L;
spinCounter = 0;
while (!client.isConnected(serverDescriptor)) {
  spinCounter++;
  if (spinCounter > spinsAllowed) {
    logger.error("Failed to establish client connection after {} spins", spinsAllowed);
    return;
  }
  logger.info("Waiting {} millis for client to connect", waitMillis);
  Thread.sleep(waitMillis);
}
assertTrue(client.isConnected(serverDescriptor));

// Push multiple requests to server
final int requestsSent = 2;
for (int iter = 0; iter < requestsSent; iter++) {
  assertTrue(client.dispatchRequest(new TinyRequest(idProvider, ExchangeType.NORMAL), serverDescriptor));
}

// Check that the server processed all requests
long expectedServerResponses = requestsSent;
waitMillis = 200L;
spinCounter = 0;
while (expectedServerResponses != server.getAllServerResponses()) {
  spinCounter++;
  if (spinCounter > spinsAllowed) {
    logger.error("Failed to receive all expectedServerResponses:{} after {} spins", expectedServerResponses, spinsAllowed);
    break;
  }
  logger.info("Waiting {} millis for receiving all expectedServerResponses:{}, serverReceived:{}, serverResponses:{}",
    waitMillis, expectedServerResponses, server.getAllServerRequests(),
    server.getAllServerResponses());
  Thread.sleep(waitMillis);
}
assertEquals(expectedServerResponses, server.getAllServerResponses());

// Douse client
assertTrue(client.severeConnection(serverDescriptor));
client.stopClient();
assertFalse(client.isClientRunning());
assertFalse(client.isConnected(serverDescriptor));
clientThread.interrupt();

// Douse server
server.stopServer();
serverThread.interrupt();
```

## More Example Usage
Take a look at the Reference Implementation in TransceiverRI for more inspiration on how to minimally extend the transceiver for use.
