package com.github.tinytcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;

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

  /**
   * Let's test this for 2 servers and 5 clients::<br/>
   * 
   * 1a. server.start()<br/>
   * 1b. server.isRunning()<br/>
   * 2a. client.start() - what if client is started before server - can it spin?<br/>
   * 2b. client.isRunning()<br/>
   * 3. client.sendToServer(TinyRequest)<br/>
   * 4a. client.stop()<br/>
   * 4b. client.isRunning()<br/>
   * 5a. server.stop()<br/>
   * 5b. server.isRunning()<br/>
   */
  @Test
  public void testTinyTCPLifecycle() throws Exception {
    // Fire up 2 servers
    final String serverOneHost = "localhost";
    final int serverOnePort = 9999;
    final InetSocketAddress serverOneAddress =
        InetSocketAddress.createUnresolved(serverOneHost, serverOnePort);
    final TinyTCPServer serverOne = new TinyTCPServer(serverOneAddress);

    final String serverTwoHost = "localhost";
    final int serverTwoPort = 8888;
    final InetSocketAddress serverTwoAddress =
        InetSocketAddress.createUnresolved(serverTwoHost, serverTwoPort);
    final TinyTCPServer serverTwo = new TinyTCPServer(serverTwoAddress);

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
    final TinyTCPClient clientOne = new TinyTCPClient(serverOneAddress, false);
    Thread clientOneThread = new Thread() {
      {
        setName("test-client-00");
      }

      public void run() {
        try {
          clientOne.start();
        } catch (Exception e) {
        }
      }
    };
    final TinyTCPClient clientTwo = new TinyTCPClient(serverOneAddress, false);
    Thread clientTwoThread = new Thread() {
      {
        setName("test-client-01");
      }

      public void run() {
        try {
          clientTwo.start();
        } catch (Exception e) {
        }
      }
    };
    final TinyTCPClient clientThree = new TinyTCPClient(serverOneAddress, false);
    Thread clientThreeThread = new Thread() {
      {
        setName("test-client-02");
      }

      public void run() {
        try {
          clientThree.start();
        } catch (Exception e) {
        }
      }
    };

    // Init 2 clients to serverTwo
    final TinyTCPClient clientFour = new TinyTCPClient(serverTwoAddress, false);
    Thread clientFourThread = new Thread() {
      {
        setName("test-client-10");
      }

      public void run() {
        try {
          clientFour.start();
        } catch (Exception e) {
        }
      }
    };
    final TinyTCPClient clientFive = new TinyTCPClient(serverTwoAddress, false);
    Thread clientFiveThread = new Thread() {
      {
        setName("test-client-11");
      }

      public void run() {
        try {
          clientFive.start();
        } catch (Exception e) {
        }
      }
    };

    // Fire up all the clients
    clientOneThread.start();
    waitMillis = 50L;
    spinCounter = 0;
    while (!clientOne.isRunning()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to bootstrap client one after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client one to bootstrap", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientOne.isRunning());

    clientTwoThread.start();
    spinCounter = 0;
    while (!clientTwo.isRunning()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to bootstrap client two after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client two to bootstrap", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientTwo.isRunning());

    clientThreeThread.start();
    spinCounter = 0;
    while (!clientThree.isRunning()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to bootstrap client three after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client three to bootstrap", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientThree.isRunning());

    clientFourThread.start();
    spinCounter = 0;
    while (!clientFour.isRunning()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to bootstrap client four after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client four to bootstrap", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientFour.isRunning());

    clientFiveThread.start();
    spinCounter = 0;
    while (!clientFive.isRunning()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to bootstrap client five after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client five to bootstrap", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientFive.isRunning());

    // push 3 requests (intended for serverOne)
    assertTrue(clientOne.sendToServer(serverOneAddress, new TinyRequest()));
    assertTrue(clientTwo.sendToServer(serverOneAddress, new TinyRequest()));
    assertTrue(clientThree.sendToServer(serverOneAddress, new TinyRequest()));

    // push another 2 requests (intended for serverOne)
    assertTrue(clientTwo.sendToServer(serverOneAddress, new TinyRequest()));
    assertTrue(clientOne.sendToServer(serverOneAddress, new TinyRequest()));

    // push 3 requests (intended for serverTwo)
    assertTrue(clientFour.sendToServer(serverTwoAddress, new TinyRequest()));
    assertTrue(clientFive.sendToServer(serverTwoAddress, new TinyRequest()));
    assertTrue(clientFour.sendToServer(serverTwoAddress, new TinyRequest()));

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
    clientOneThread.interrupt();

    clientTwo.stop();
    assertFalse(clientTwo.isRunning());
    clientTwoThread.interrupt();

    clientThree.stop();
    assertFalse(clientThree.isRunning());
    clientThreeThread.interrupt();

    clientFour.stop();
    assertFalse(clientFour.isRunning());
    clientFourThread.interrupt();

    clientFive.stop();
    assertFalse(clientFive.isRunning());
    clientFiveThread.interrupt();

    // Douse servers
    serverOne.stop();
    serverTwo.stop();
    serverThread.interrupt();
  }

}
