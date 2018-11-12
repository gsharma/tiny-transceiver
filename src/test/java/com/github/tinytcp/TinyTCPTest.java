package com.github.tinytcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Ignore;
import org.junit.Test;

/**
 * TinyTCP client-server tests
 * 
 * @author gaurav
 */
public final class TinyTCPTest {
  private static final Logger logger = LogManager.getLogger(TinyTCPTest.class.getSimpleName());
  private final IdProvider idProvider = new TestIdProvider();

  /**
   * Let's test this for 2 servers and 5 clients::<br/>
   * 
   * 1a. server.start()<br/>
   * 1b. server.isRunning()<br/>
   * 2a. client.isRunning()<br/>
   * 2b. client.establishConnection(ServerDescriptor) - what if client is started before server -
   * can it spin?<br/>
   * 3. client.sendToServer(ServerDescriptor, TinyRequest)<br/>
   * 4a. client.stop()<br/>
   * 4b. client.isRunning()<br/>
   * 5a. server.stop()<br/>
   * 5b. server.isRunning()<br/>
   */
  @Ignore
  @Test
  public void test5ClientsTo2Servers() throws Exception {
    // Fire up 2 servers
    final String serverOneHost = "localhost";
    final int serverOnePort = 9999;
    final ServerDescriptor serverOneDescriptor =
        new ServerDescriptor(serverOneHost, serverOnePort, false);
    final TinyTransceiver serverOne = new TransceiverRI(idProvider);

    final String serverTwoHost = "localhost";
    final int serverTwoPort = 8888;
    final ServerDescriptor serverTwoDescriptor =
        new ServerDescriptor(serverTwoHost, serverTwoPort, false);
    final TinyTransceiver serverTwo = new TransceiverRI(idProvider);

    Thread serverThread = new Thread() {
      {
        setName("test-server");
      }

      public void run() {
        try {
          serverOne.startServer(serverOneDescriptor);
          serverTwo.startServer(serverTwoDescriptor);
        } catch (Exception e) {
        }
      }
    };
    serverThread.start();
    int spinCounter = 0, spinsAllowed = 50;
    long waitMillis = 100L;
    while (!serverOne.isServerRunning()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to bootstrap serverOne after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for serverOne to bootstrap", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(serverOne.isServerRunning());

    spinCounter = 0; // reset spinner
    while (!serverTwo.isServerRunning()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to bootstrap serverTwo after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for serverTwo to bootstrap", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(serverTwo.isServerRunning());

    // Init 3 clients to serverOne
    final TinyTransceiver clientOne = new TransceiverRI(idProvider);
    clientOne.startClient();
    assertTrue(clientOne.isClientRunning());
    Thread clientOneThread = new Thread() {
      {
        setName("test-client-00");
      }

      public void run() {
        try {
          assertTrue(clientOne.connectToServer(serverOneDescriptor));
        } catch (Exception e) {
        }
      }
    };
    final TinyTransceiver clientTwo = new TransceiverRI(idProvider);
    clientTwo.startClient();
    assertTrue(clientTwo.isClientRunning());
    Thread clientTwoThread = new Thread() {
      {
        setName("test-client-01");
      }

      public void run() {
        try {
          assertTrue(clientTwo.connectToServer(serverOneDescriptor));
        } catch (Exception e) {
        }
      }
    };
    final TinyTransceiver clientThree = new TransceiverRI(idProvider);
    clientThree.startClient();
    assertTrue(clientThree.isClientRunning());
    Thread clientThreeThread = new Thread() {
      {
        setName("test-client-02");
      }

      public void run() {
        try {
          assertTrue(clientThree.connectToServer(serverOneDescriptor));
        } catch (Exception e) {
        }
      }
    };

    // Init 2 clients to serverTwo
    final TinyTransceiver clientFour = new TransceiverRI(idProvider);
    clientFour.startClient();
    assertTrue(clientFour.isClientRunning());
    Thread clientFourThread = new Thread() {
      {
        setName("test-client-10");
      }

      public void run() {
        try {
          assertTrue(clientFour.connectToServer(serverTwoDescriptor));
        } catch (Exception e) {
        }
      }
    };
    final TinyTransceiver clientFive = new TransceiverRI(idProvider);
    clientFive.startClient();
    assertTrue(clientFive.isClientRunning());
    Thread clientFiveThread = new Thread() {
      {
        setName("test-client-11");
      }

      public void run() {
        try {
          assertTrue(clientFive.connectToServer(serverTwoDescriptor));
        } catch (Exception e) {
        }
      }
    };

    // Fire up all the clients
    clientOneThread.start();
    waitMillis = 50L;
    spinCounter = 0;
    while (!clientOne.isConnected(serverOneDescriptor)) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to establish client one connection after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client one to connect", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientOne.isConnected(serverOneDescriptor));

    clientTwoThread.start();
    spinCounter = 0;
    while (!clientTwo.isConnected(serverOneDescriptor)) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to establish client two connection after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client two to connect", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientTwo.isConnected(serverOneDescriptor));

    clientThreeThread.start();
    spinCounter = 0;
    while (!clientThree.isConnected(serverOneDescriptor)) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to establish client three connection after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client three to connect", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientThree.isConnected(serverOneDescriptor));

    clientFourThread.start();
    spinCounter = 0;
    while (!clientFour.isConnected(serverTwoDescriptor)) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to establish client four connection after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client four to connect", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientFour.isConnected(serverTwoDescriptor));

    clientFiveThread.start();
    spinCounter = 0;
    while (!clientFive.isConnected(serverTwoDescriptor)) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to establish client five connection after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client five to connect", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientFive.isConnected(serverTwoDescriptor));

    // push 3 requests (intended for serverOne)
    assertTrue(clientOne.dispatchRequest(new TinyRequest(idProvider), serverOneDescriptor));
    assertTrue(clientTwo.dispatchRequest(new TinyRequest(idProvider), serverOneDescriptor));
    assertTrue(clientThree.dispatchRequest(new TinyRequest(idProvider), serverOneDescriptor));

    // push another 2 requests (intended for serverOne)
    assertTrue(clientTwo.dispatchRequest(new TinyRequest(idProvider), serverOneDescriptor));
    assertTrue(clientOne.dispatchRequest(new TinyRequest(idProvider), serverOneDescriptor));

    // push 3 requests (intended for serverTwo)
    assertTrue(clientFour.dispatchRequest(new TinyRequest(idProvider), serverTwoDescriptor));
    assertTrue(clientFive.dispatchRequest(new TinyRequest(idProvider), serverTwoDescriptor));
    assertTrue(clientFour.dispatchRequest(new TinyRequest(idProvider), serverTwoDescriptor));

    final long expectedServerOneResponses = 5L;
    waitMillis = 50L;
    spinCounter = 0;
    while (expectedServerOneResponses != serverOne.getAllServerResponses()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to receive all expectedServerOneResponses:{} after {} spins",
            expectedServerOneResponses, spinsAllowed);
        break;
      }
      logger.info(
          "Waiting {} millis for receiving all expectedServerOneResponses:{}, serverReceived:{}, serverResponses:{}",
          waitMillis, expectedServerOneResponses, serverOne.getAllServerRequests(),
          serverOne.getAllServerResponses());
      Thread.sleep(waitMillis);
    }
    assertEquals(expectedServerOneResponses, serverOne.getAllServerResponses());

    final long expectedServerTwoResponses = 3L;
    waitMillis = 50L;
    spinCounter = 0;
    while (expectedServerTwoResponses != serverTwo.getAllServerResponses()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to receive all expectedServerTwoResponses:{} after {} spins",
            expectedServerTwoResponses, spinsAllowed);
        break;
      }
      logger.info(
          "Waiting {} millis for receiving all expectedServerTwoResponses:{}, serverReceived:{}, serverResponses:{}",
          waitMillis, expectedServerTwoResponses, serverTwo.getAllServerRequests(),
          serverOne.getAllServerResponses());
      Thread.sleep(waitMillis);
    }
    assertEquals(expectedServerTwoResponses, serverTwo.getAllServerResponses());

    // Douse clients
    clientOne.stopClient();
    assertFalse(clientOne.isClientRunning());
    assertFalse(clientOne.isConnected(serverOneDescriptor));
    clientOneThread.interrupt();

    clientTwo.stopClient();
    assertFalse(clientTwo.isClientRunning());
    assertFalse(clientTwo.isConnected(serverOneDescriptor));
    clientTwoThread.interrupt();

    clientThree.stopClient();
    assertFalse(clientThree.isClientRunning());
    assertFalse(clientThree.isConnected(serverOneDescriptor));
    clientThreeThread.interrupt();

    clientFour.stopClient();
    assertFalse(clientFour.isClientRunning());
    assertFalse(clientFour.isConnected(serverTwoDescriptor));
    clientFourThread.interrupt();

    clientFive.stopClient();
    assertFalse(clientFive.isClientRunning());
    assertFalse(clientFive.isConnected(serverTwoDescriptor));
    clientFiveThread.interrupt();

    // Douse servers
    serverOne.stopServer();
    serverTwo.stopServer();
    serverThread.interrupt();
  }

  @Test
  public void test1Client1Server() throws Exception {
    // Fire up the server
    final String serverHost = "localhost";
    final int serverPort = 7500;
    final ServerDescriptor serverDescriptor = new ServerDescriptor(serverHost, serverPort, false);
    final TinyTransceiver server = new TransceiverRI(idProvider);
    Thread serverThread = new Thread() {
      {
        setName("test-server");
      }

      public void run() {
        try {
          server.startServer(serverDescriptor);
        } catch (Exception e) {
        }
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
      {
        setName("test-client");
      }

      public void run() {
        try {
          assertTrue(client.connectToServer(serverDescriptor));
        } catch (Exception e) {
        }
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
    final int requestsSent = 5;
    for (int iter = 0; iter < requestsSent; iter++) {
      assertTrue(client.dispatchRequest(new TinyRequest(idProvider), serverDescriptor));
    }

    // Check that the server processed all requests
    long expectedServerResponses = requestsSent;
    waitMillis = 200L;
    spinCounter = 0;
    while (expectedServerResponses != server.getAllServerResponses()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to receive all expectedServerResponses:{} after {} spins",
            expectedServerResponses, spinsAllowed);
        break;
      }
      logger.info(
          "Waiting {} millis for receiving all expectedServerResponses:{}, serverReceived:{}, serverResponses:{}",
          waitMillis, expectedServerResponses, server.getAllServerRequests(),
          server.getAllServerResponses());
      Thread.sleep(waitMillis);
    }
    assertEquals(expectedServerResponses, server.getAllServerResponses());

    // All good so far; let's repeat with dropping connection, connection re-establishment and a few
    // more requests
    /*
     * assertTrue(client.severeConnection(serverDescriptor));
     * assertTrue(client.establishConnection(serverDescriptor)); for (int iter = 0; iter <
     * requestsSent; iter++) { assertTrue(client.sendToServer(serverDescriptor, new
     * TinyRequest(idProvider))); } spinCounter = 0; expectedServerResponses += requestsSent; while
     * (expectedServerResponses != server.getAllResponsesSent()) { spinCounter++; if (spinCounter >
     * spinsAllowed) {
     * logger.error("Failed to receive all expectedServerResponses:{} after {} spins",
     * expectedServerResponses, spinsAllowed); break; } logger.info(
     * "Waiting {} millis for receiving all expectedServerResponses:{}, serverReceived:{}, serverResponses:{}"
     * , waitMillis, expectedServerResponses, server.getAllRequestsReceived(),
     * server.getAllResponsesSent()); Thread.sleep(waitMillis); }
     * assertEquals(expectedServerResponses, server.getAllResponsesSent());
     */

    // Douse client
    client.stopClient();
    assertFalse(client.isClientRunning());
    assertFalse(client.isConnected(serverDescriptor));
    clientThread.interrupt();

    // Douse server
    server.stopServer();
    serverThread.interrupt();
  }

}
