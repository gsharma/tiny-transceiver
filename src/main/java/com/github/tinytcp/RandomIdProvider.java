package com.github.tinytcp;

import java.util.UUID;

/**
 * A random id provider relying on random Type 4 UUIDs.
 * 
 * @author gaurav
 */
public final class RandomIdProvider implements IdProvider {

  @Override
  public String id() {
    return UUID.randomUUID().toString();
  }

}
