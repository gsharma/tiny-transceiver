package com.github.tinytcp;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Test id provider.
 * 
 * @author gaurav
 */
public class TestIdProvider implements IdProvider {
  private final AtomicLong idSource = new AtomicLong(1);

  @Override
  public String id() {
    return Long.toString(idSource.incrementAndGet());
  }

}
