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
public final class TinyRequest implements Request {
  private static final transient Logger logger =
      LogManager.getLogger(TinyRequest.class.getSimpleName());
  // @JsonIgnore
  // private IdProvider idProvider;

  private String id;
  private ExchangeType type;

  public TinyRequest(final IdProvider idProvider, final ExchangeType type) {
    Objects.requireNonNull(idProvider, "idProvider cannot be null");
    Objects.requireNonNull(type, "exchangeType cannot be null");
    // this.idProvider = idProvider;
    this.id = idProvider.id();
    this.type = type;
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
  public ExchangeType getType() {
    return type;
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
    builder.append("TinyRequest[id:").append(id).append(",type:").append(type).append("]");
    return builder.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    result = prime * result + ((type == null) ? 0 : type.hashCode());
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
    if (type != other.type) {
      return false;
    }
    return true;
  }

  // exists to help jackson deserialize; not for use otherwise
  TinyRequest() {}

  // exists to help jackson deserialize; not for use otherwise
  void setId(final String id) {
    this.id = id;
  }

  // exists to help jackson deserialize; not for use otherwise
  void setType(final ExchangeType type) {
    this.type = type;
  }

}
