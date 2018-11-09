package com.github.tinytcp;

import static org.junit.Assert.*;

import java.util.Optional;

import org.junit.Test;

/**
 * Tests for the sanity/correctness of Request, Response serialization-deserialization.
 * 
 * @author gaurav
 */
public class SerdeTest {
  private final IdProvider idProvider = new RandomIdProvider();

  @Test
  public void testRequestSerDe() {
    final Request request = new TinyRequest(idProvider);
    final byte[] serializedRequest = request.serialize();
    assertEquals(45, serializedRequest.length);
    final Request deserializedRequest = new TinyRequest(idProvider).deserialize(serializedRequest);
    assertEquals(request, deserializedRequest);
    assertEquals(request.getId(), deserializedRequest.getId());
  }

  @Test
  public void testResponseSerDe() {
    final Response response = new TinyResponse(idProvider, Optional.empty());
    final byte[] serializedResponse = response.serialize();
    assertEquals(75, serializedResponse.length);
    final Response deserializedResponse =
        new TinyResponse(idProvider, Optional.empty()).deserialize(serializedResponse);
    assertEquals(response, deserializedResponse);
    assertEquals(response.getId(), deserializedResponse.getId());
  }

}
