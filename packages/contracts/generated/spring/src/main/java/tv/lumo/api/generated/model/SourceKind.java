package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What the user registered.  `M3U_FILE` designates a playlist uploaded as a file. It exists in the domain model and may be returned by read operations, but v1 exposes no endpoint that creates one: `POST /sources` is JSON-only and accepts `M3U_URL` and `XTREAM`. A multipart upload operation is not part of this contract. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public enum SourceKind {
  
  M3_U_URL("M3U_URL"),
  
  M3_U_FILE("M3U_FILE"),
  
  XTREAM("XTREAM");

  private final String value;

  SourceKind(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static SourceKind fromValue(String value) {
    for (SourceKind b : SourceKind.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

