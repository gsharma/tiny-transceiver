package com.github.tinytcp;

import java.util.Objects;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Reference Implementation of a lightweight Response.
 * 
 * @author gaurav
 */
public class TinyResponse implements Response {
  @JsonIgnore
  private static final Logger logger = LogManager.getLogger(TinyResponse.class.getSimpleName());
  // @JsonIgnore
  // private IdProvider idProvider;

  private String id;
  private String requestId;
  private String error;

  public TinyResponse(final IdProvider idProvider, final Optional<String> requestId) {
    Objects.requireNonNull(idProvider, "idProvider cannot be null");
    // this.idProvider = idProvider;
    this.id = idProvider.id();
    if (requestId.isPresent()) {
      this.requestId = requestId.get();
    }
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
  public Response deserialize(final byte[] flattenedResponse) {
    Response deserializedResponse = null;
    try {
      deserializedResponse =
          InternalLib.getObjectMapper().readValue(flattenedResponse, TinyResponse.class);
    } catch (Exception serDeProblem) {
      logger.error("Encountered error during deserialization of flattened response", serDeProblem);
    }
    return deserializedResponse;
  }

  @Override
  public String getRequestId() {
    return requestId;
  }

  @Override
  public String getError() {
    return error;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("TinyResponse[id:").append(id).append(",requestId:").append(requestId)
        .append(",error:").append(error).append("]");
    return builder.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    result = prime * result + ((requestId == null) ? 0 : requestId.hashCode());
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
    if (!(obj instanceof TinyResponse)) {
      return false;
    }
    TinyResponse other = (TinyResponse) obj;
    if (id == null) {
      if (other.id != null) {
        return false;
      }
    } else if (!id.equals(other.id)) {
      return false;
    }
    if (requestId == null) {
      if (other.requestId != null) {
        return false;
      }
    } else if (!requestId.equals(other.requestId)) {
      return false;
    }
    return true;
  }

  // exists to help jackson deserialize
  TinyResponse() {}

  // exists to help jackson deserialize
  void setId(final String id) {
    this.id = id;
  }

  // exists to help jackson deserialize
  void setError(final String error) {
    this.error = error;
  }

}
