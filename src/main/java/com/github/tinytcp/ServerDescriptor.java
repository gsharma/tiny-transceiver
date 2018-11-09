package com.github.tinytcp;

import java.net.InetSocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An immutable descriptor for a server.
 * 
 * @author gaurav
 */
public final class ServerDescriptor {
  private static final transient Logger logger =
      LogManager.getLogger(ServerDescriptor.class.getSimpleName());

  private final InetSocketAddress address;
  private final boolean connectLocally;

  public ServerDescriptor(final String host, final int port, final boolean connectLocally) {
    this.address = InetSocketAddress.createUnresolved(host, port);
    this.connectLocally = connectLocally;
  }

  public static Logger getLogger() {
    return logger;
  }

  public InetSocketAddress getAddress() {
    return address;
  }

  public boolean connectLocally() {
    return connectLocally;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("ServerDescriptor[address:").append(address).append(",connectLocally:")
        .append(connectLocally).append("]");
    return builder.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((address == null) ? 0 : address.hashCode());
    result = prime * result + (connectLocally ? 1231 : 1237);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof ServerDescriptor)) {
      return false;
    }
    ServerDescriptor other = (ServerDescriptor) obj;
    if (address == null) {
      if (other.address != null) {
        return false;
      }
    } else if (!address.equals(other.address)) {
      return false;
    }
    if (connectLocally != other.connectLocally) {
      return false;
    }
    return true;
  }

}
