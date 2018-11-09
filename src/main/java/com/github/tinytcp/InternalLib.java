package com.github.tinytcp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Internal libs with various reusable functions.
 * 
 * @author gaurav
 */
final class InternalLib {
  private static final transient Logger logger =
      LogManager.getLogger(InternalLib.class.getSimpleName());
  private static final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Get ObjectMapper for json ser-de
   */
  public static ObjectMapper getObjectMapper() {
    return objectMapper;
  }

}
