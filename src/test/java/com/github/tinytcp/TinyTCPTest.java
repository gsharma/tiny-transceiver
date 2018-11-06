package com.github.tinytcp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * TinyTCP client-server tests
 * 
 * @author gaurav
 */
public final class TinyTCPTest {
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
    Thread.sleep(500L);
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
    Thread.sleep(100L);
    assertTrue(clientOne.running());
    assertTrue(clientTwo.running());
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
