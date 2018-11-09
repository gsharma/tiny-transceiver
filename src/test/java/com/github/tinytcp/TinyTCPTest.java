package com.github.tinytcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
  @Test
  public void test5ClientsTo2Servers() throws Exception {
    // Fire up 2 servers
    final String serverOneHost = "localhost";
    final int serverOnePort = 9999;
    final ServerDescriptor serverOneDescriptor =
        new ServerDescriptor(serverOneHost, serverOnePort, false);
    final TinyTCPServer serverOne = new TinyTCPServer(idProvider, serverOneDescriptor);

    final String serverTwoHost = "localhost";
    final int serverTwoPort = 8888;
    final ServerDescriptor serverTwoDescriptor =
        new ServerDescriptor(serverTwoHost, serverTwoPort, false);
    final TinyTCPServer serverTwo = new TinyTCPServer(idProvider, serverTwoDescriptor);

    Thread serverThread = new Thread() {
      {
        setName("test-server");
      }

      public void run() {
        try {
          serverOne.start();
          serverTwo.start();
        } catch (Exception e) {
        }
      }
    };
    serverThread.start();
    int spinCounter = 0, spinsAllowed = 50;
    long waitMillis = 100L;
    while (!serverOne.isRunning()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to bootstrap serverOne after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for serverOne to bootstrap", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(serverOne.isRunning());

    spinCounter = 0; // reset spinner
    while (!serverTwo.isRunning()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to bootstrap serverTwo after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for serverTwo to bootstrap", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(serverTwo.isRunning());

    // Init 3 clients to serverOne
    final TinyTCPClient clientOne = new TinyTCPClient(idProvider);
    assertTrue(clientOne.isRunning());
    Thread clientOneThread = new Thread() {
      {
        setName("test-client-00");
      }

      public void run() {
        try {
          assertTrue(clientOne.establishConnection(serverOneDescriptor));
        } catch (Exception e) {
        }
      }
    };
    final TinyTCPClient clientTwo = new TinyTCPClient(idProvider);
    assertTrue(clientTwo.isRunning());
    Thread clientTwoThread = new Thread() {
      {
        setName("test-client-01");
      }

      public void run() {
        try {
          assertTrue(clientTwo.establishConnection(serverOneDescriptor));
        } catch (Exception e) {
        }
      }
    };
    final TinyTCPClient clientThree = new TinyTCPClient(idProvider);
    assertTrue(clientThree.isRunning());
    Thread clientThreeThread = new Thread() {
      {
        setName("test-client-02");
      }

      public void run() {
        try {
          assertTrue(clientThree.establishConnection(serverOneDescriptor));
        } catch (Exception e) {
        }
      }
    };

    // Init 2 clients to serverTwo
    final TinyTCPClient clientFour = new TinyTCPClient(idProvider);
    assertTrue(clientFour.isRunning());
    Thread clientFourThread = new Thread() {
      {
        setName("test-client-10");
      }

      public void run() {
        try {
          assertTrue(clientFour.establishConnection(serverTwoDescriptor));
        } catch (Exception e) {
        }
      }
    };
    final TinyTCPClient clientFive = new TinyTCPClient(idProvider);
    assertTrue(clientFive.isRunning());
    Thread clientFiveThread = new Thread() {
      {
        setName("test-client-11");
      }

      public void run() {
        try {
          assertTrue(clientFive.establishConnection(serverTwoDescriptor));
        } catch (Exception e) {
        }
      }
    };

    // Fire up all the clients
    clientOneThread.start();
    waitMillis = 50L;
    spinCounter = 0;
    while (!clientOne.isConnectionEstablished(serverOneDescriptor)) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to establish client one connection after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client one to connect", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientOne.isConnectionEstablished(serverOneDescriptor));

    clientTwoThread.start();
    spinCounter = 0;
    while (!clientTwo.isConnectionEstablished(serverOneDescriptor)) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to establish client two connection after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client two to connect", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientTwo.isConnectionEstablished(serverOneDescriptor));

    clientThreeThread.start();
    spinCounter = 0;
    while (!clientThree.isConnectionEstablished(serverOneDescriptor)) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to establish client three connection after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client three to connect", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientThree.isConnectionEstablished(serverOneDescriptor));

    clientFourThread.start();
    spinCounter = 0;
    while (!clientFour.isConnectionEstablished(serverTwoDescriptor)) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to establish client four connection after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client four to connect", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientFour.isConnectionEstablished(serverTwoDescriptor));

    clientFiveThread.start();
    spinCounter = 0;
    while (!clientFive.isConnectionEstablished(serverTwoDescriptor)) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to establish client five connection after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client five to connect", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientFive.isConnectionEstablished(serverTwoDescriptor));

    // push 3 requests (intended for serverOne)
    assertTrue(clientOne.sendToServer(serverOneDescriptor, new TinyRequest(idProvider)));
    assertTrue(clientTwo.sendToServer(serverOneDescriptor, new TinyRequest(idProvider)));
    assertTrue(clientThree.sendToServer(serverOneDescriptor, new TinyRequest(idProvider)));

    // push another 2 requests (intended for serverOne)
    assertTrue(clientTwo.sendToServer(serverOneDescriptor, new TinyRequest(idProvider)));
    assertTrue(clientOne.sendToServer(serverOneDescriptor, new TinyRequest(idProvider)));

    // push 3 requests (intended for serverTwo)
    assertTrue(clientFour.sendToServer(serverTwoDescriptor, new TinyRequest(idProvider)));
    assertTrue(clientFive.sendToServer(serverTwoDescriptor, new TinyRequest(idProvider)));
    assertTrue(clientFour.sendToServer(serverTwoDescriptor, new TinyRequest(idProvider)));

    final long expectedServerOneResponses = 5L;
    waitMillis = 50L;
    spinCounter = 0;
    while (expectedServerOneResponses != serverOne.getAllResponsesSent()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to receive all expectedServerOneResponses:{} after {} spins",
            expectedServerOneResponses, spinsAllowed);
        break;
      }
      logger.info(
          "Waiting {} millis for receiving all expectedServerOneResponses:{}, serverReceived:{}, serverResponses:{}",
          waitMillis, expectedServerOneResponses, serverOne.getAllRequestsReceived(),
          serverOne.getAllResponsesSent());
      Thread.sleep(waitMillis);
    }
    assertEquals(expectedServerOneResponses, serverOne.getAllResponsesSent());

    final long expectedServerTwoResponses = 3L;
    waitMillis = 50L;
    spinCounter = 0;
    while (expectedServerTwoResponses != serverTwo.getAllResponsesSent()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to receive all expectedServerTwoResponses:{} after {} spins",
            expectedServerTwoResponses, spinsAllowed);
        break;
      }
      logger.info(
          "Waiting {} millis for receiving all expectedServerTwoResponses:{}, serverReceived:{}, serverResponses:{}",
          waitMillis, expectedServerTwoResponses, serverTwo.getAllRequestsReceived(),
          serverOne.getAllResponsesSent());
      Thread.sleep(waitMillis);
    }
    assertEquals(expectedServerTwoResponses, serverTwo.getAllResponsesSent());

    // Douse clients
    clientOne.stop();
    assertFalse(clientOne.isRunning());
    assertFalse(clientOne.isConnectionEstablished(serverOneDescriptor));
    clientOneThread.interrupt();

    clientTwo.stop();
    assertFalse(clientTwo.isRunning());
    assertFalse(clientTwo.isConnectionEstablished(serverOneDescriptor));
    clientTwoThread.interrupt();

    clientThree.stop();
    assertFalse(clientThree.isRunning());
    assertFalse(clientThree.isConnectionEstablished(serverOneDescriptor));
    clientThreeThread.interrupt();

    clientFour.stop();
    assertFalse(clientFour.isRunning());
    assertFalse(clientFour.isConnectionEstablished(serverTwoDescriptor));
    clientFourThread.interrupt();

    clientFive.stop();
    assertFalse(clientFive.isRunning());
    assertFalse(clientFive.isConnectionEstablished(serverTwoDescriptor));
    clientFiveThread.interrupt();

    // Douse servers
    serverOne.stop();
    serverTwo.stop();
    serverThread.interrupt();
  }

  @Test
  public void test1Client1Server() throws Exception {
    // Fire up the server
    final String serverHost = "localhost";
    final int serverPort = 9999;
    final ServerDescriptor serverDescriptor = new ServerDescriptor(serverHost, serverPort, false);
    final TinyTCPServer server = new TinyTCPServer(idProvider, serverDescriptor);
    Thread serverThread = new Thread() {
      {
        setName("test-server");
      }

      public void run() {
        try {
          server.start();
        } catch (Exception e) {
        }
      }
    };
    serverThread.start();
    int spinCounter = 0, spinsAllowed = 100;
    long waitMillis = 100L;
    while (!server.isRunning()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to bootstrap server after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for server to bootstrap", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(server.isRunning());

    // Fire up the client and connect to server
    final TinyTCPClient client = new TinyTCPClient(idProvider);
    assertTrue(client.isRunning());
    Thread clientThread = new Thread() {
      {
        setName("test-client");
      }

      public void run() {
        try {
          assertTrue(client.establishConnection(serverDescriptor));
        } catch (Exception e) {
        }
      }
    };
    clientThread.start();
    waitMillis = 50L;
    spinCounter = 0;
    while (!client.isConnectionEstablished(serverDescriptor)) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to establish client connection after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client to connect", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(client.isConnectionEstablished(serverDescriptor));

    // Push multiple requests to server
    int requestsSent = 2;
    for (int iter = 0; iter < requestsSent; iter++) {
      assertTrue(client.sendToServer(serverDescriptor, new TinyRequest(idProvider)));
    }

    // Check that the server processed all requests
    long expectedServerResponses = requestsSent;
    waitMillis = 500L;
    spinCounter = 0;
    while (expectedServerResponses != server.getAllResponsesSent()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to receive all expectedServerResponses:{} after {} spins",
            expectedServerResponses, spinsAllowed);
        break;
      }
      logger.info(
          "Waiting {} millis for receiving all expectedServerResponses:{}, serverReceived:{}, serverResponses:{}",
          waitMillis, expectedServerResponses, server.getAllRequestsReceived(),
          server.getAllResponsesSent());
      Thread.sleep(waitMillis);
    }
    assertEquals(expectedServerResponses, server.getAllResponsesSent());

    // All good so far; let's repeat with dropping connection, connection re-establishment and a few
    // more requests
    assertTrue(client.severeConnection(serverDescriptor));
    assertTrue(client.establishConnection(serverDescriptor));
    for (int iter = 0; iter < requestsSent; iter++) {
      assertTrue(client.sendToServer(serverDescriptor, new TinyRequest(idProvider)));
    }
    spinCounter = 0;
    expectedServerResponses += requestsSent;
    while (expectedServerResponses != server.getAllResponsesSent()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to receive all expectedServerResponses:{} after {} spins",
            expectedServerResponses, spinsAllowed);
        break;
      }
      logger.info(
          "Waiting {} millis for receiving all expectedServerResponses:{}, serverReceived:{}, serverResponses:{}",
          waitMillis, expectedServerResponses, server.getAllRequestsReceived(),
          server.getAllResponsesSent());
      Thread.sleep(waitMillis);
    }
    assertEquals(expectedServerResponses, server.getAllResponsesSent());

    // Douse client
    client.stop();
    assertFalse(client.isRunning());
    assertFalse(client.isConnectionEstablished(serverDescriptor));
    clientThread.interrupt();

    // Douse server
    server.stop();
    serverThread.interrupt();
  }

}
