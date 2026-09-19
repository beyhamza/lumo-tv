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
 * Ingestion lifecycle.  `PENDING` → accepted, not started. `SYNCING` → ingestion running. `READY` → catalogue usable. `ERROR` → ingestion failed, see `error_code`.  Clients poll until `READY` or `ERROR`; those are the only terminal states.  The status describes the **latest attempt**, not whether there is a catalogue. That is `last_synced_at`: once non-null, the catalogue listings answer in every status. Reading a catalogue and playing from it are two different permissions — playback is refused while an ingestion is pending or running, and allowed again after a failed one unless the failure is about the user's credentials or subscription (see the `409` of the playback operations). 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public enum SourceStatus {
  
  PENDING("PENDING"),
  
  SYNCING("SYNCING"),
  
  READY("READY"),
  
  ERROR("ERROR");

  private final String value;

  SourceStatus(String value) {
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
  public static SourceStatus fromValue(String value) {
    for (SourceStatus b : SourceStatus.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

