package com.github.tinytcp;

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

  @Test
  public void testTinyTCPClientServer() throws Exception {
    // Fire up a server
    final TinyTCPServer server = new TinyTCPServer();
    Thread serverThread = new Thread() {
      {
        setName("driver-server");
      }

      public void run() {
        try {
          server.start();
        } catch (Exception e) {
        }
      }
    };
    serverThread.start();
    int spinCounter = 0, spinsAllowed = 50;
    long waitMillis = 100L;
    while (!server.running()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to bootstrap server after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for server to bootstrap", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(server.running());

    // Init 3 clients to the same server
    final TinyTCPClient clientOne = new TinyTCPClient();
    Thread clientOneThread = new Thread() {
      {
        setName("driver-zero");
      }

      public void run() {
        try {
          clientOne.start();
        } catch (Exception e) {
        }
      }
    };
    final TinyTCPClient clientTwo = new TinyTCPClient();
    Thread clientTwoThread = new Thread() {
      {
        setName("driver-one");
      }

      public void run() {
        try {
          clientTwo.start();
        } catch (Exception e) {
        }
      }
    };
    final TinyTCPClient clientThree = new TinyTCPClient();
    Thread clientThreeThread = new Thread() {
      {
        setName("driver-two");
      }

      public void run() {
        try {
          clientThree.start();
        } catch (Exception e) {
        }
      }
    };

    // Fire up the clients
    clientOneThread.start();
    clientTwoThread.start();
    clientThreeThread.start();
    waitMillis = 50L;
    spinCounter = 0;
    while (!clientOne.running()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to bootstrap client one after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client one to bootstrap", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientOne.running());
    spinCounter = 0;
    while (!clientTwo.running()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to bootstrap client two after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client two to bootstrap", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientTwo.running());
    spinCounter = 0;
    while (!clientThree.running()) {
      spinCounter++;
      if (spinCounter > spinsAllowed) {
        logger.error("Failed to bootstrap client three after {} spins", spinsAllowed);
        return;
      }
      logger.info("Waiting {} millis for client three to bootstrap", waitMillis);
      Thread.sleep(waitMillis);
    }
    assertTrue(clientThree.running());

    // Douse clients
    clientOne.stop();
    assertFalse(clientOne.running());
    clientOneThread.interrupt();
    clientTwo.stop();
    assertFalse(clientTwo.running());
    clientTwoThread.interrupt();
    clientThree.stop();
    assertFalse(clientThree.running());
    clientThreeThread.interrupt();

    // Douse server
    server.stop();
    serverThread.interrupt();
  }

}
