package com.github.tinytcp;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for the sanity/correctness of Request, Response serialization-deserialization.
 * 
 * @author gaurav
 */
public class SerdeTest {

  @Test
  public void testRequestSerDe() {
    final Request request = new TinyRequest();
    final byte[] serializedRequest = request.serialize();
    assertEquals(45, serializedRequest.length);
    final Request deserializedRequest = new TinyRequest().deserialize(serializedRequest);
    assertEquals(request, deserializedRequest);
    assertEquals(request.getId(), deserializedRequest.getId());
  }

  @Test
  public void testResponseSerDe() {
    final Response response = new TinyResponse();
    final byte[] serializedResponse = response.serialize();
    assertEquals(62, serializedResponse.length);
    final Response deserializedResponse = new TinyResponse().deserialize(serializedResponse);
    assertEquals(response, deserializedResponse);
    assertEquals(response.getId(), deserializedResponse.getId());
  }

}
