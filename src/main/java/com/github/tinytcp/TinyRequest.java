package com.github.tinytcp;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Reference Implementation of a lightweight Request.
 * 
 * @author gaurav
 */
public class TinyRequest implements Request {
  private static final transient Logger logger =
      LogManager.getLogger(TinyRequest.class.getSimpleName());
  // @JsonIgnore
  // private IdProvider idProvider;

  private String id;

  public TinyRequest(final IdProvider idProvider) {
    Objects.requireNonNull(idProvider, "idProvider cannot be null");
    // this.idProvider = idProvider;
    this.id = idProvider.id();
  }

  @Override
  public byte[] serialize() {
    byte[] serialized = new byte[0];
    try {
      serialized = InternalLib.getObjectMapper().writeValueAsBytes(this);
    } catch (Exception serDeProblem) {
      logger.error(String.format("Encountered error during serialization of %s", toString()),
          serDeProblem);
    }
    return serialized;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public Request deserialize(final byte[] flattenedRequest) {
    Request deserializedRequest = null;
    try {
      deserializedRequest =
          InternalLib.getObjectMapper().readValue(flattenedRequest, TinyRequest.class);
    } catch (Exception serDeProblem) {
      logger.error("Encountered error during deserialization of flattened request", serDeProblem);
    }
    return deserializedRequest;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("TinyRequest[id:").append(id).append("]");
    return builder.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((id == null) ? 0 : id.hashCode());
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
    if (!(obj instanceof TinyRequest)) {
      return false;
    }
    TinyRequest other = (TinyRequest) obj;
    if (id == null) {
      if (other.id != null) {
        return false;
      }
    } else if (!id.equals(other.id)) {
      return false;
    }
    return true;
  }

  // exists to help jackson deserialize
  TinyRequest() {}

  // exists to help jackson deserialize
  void setId(final String id) {
    this.id = id;
  }

}
