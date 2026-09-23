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
 * Whether a channel can be matched to the guide at all (C1, D2).  `MAPPED` — the channel carries a `tvg_id`. It says **only** that; it is not a promise that any programme exists for it, and an empty list under a `MAPPED` channel is a normal answer.  `NO_TVG_ID` — the channel carries none, so nothing in any guide can be attributed to it. Its programmes are always empty, and a client says so rather than showing a blank row: \"this channel has no guide identifier\" is the one explanation that helps. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public enum EpgMappingStatus {
  
  MAPPED("MAPPED"),
  
  NO_TVG_ID("NO_TVG_ID");

  private final String value;

  EpgMappingStatus(String value) {
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
  public static EpgMappingStatus fromValue(String value) {
    for (EpgMappingStatus b : EpgMappingStatus.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

