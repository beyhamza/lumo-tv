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
 * The outcome of the latest import of the guide into this server, as recorded on the source (C1, D3).  | Value | Meaning | |---|---| | `UNKNOWN` | No import has been recorded under the current configuration: the source predates this field, or its guide settings changed since the last one. Whatever programmes are stored are unverified — not wrong, not fresh. | | `RUNNING` | An import is writing now. Programmes may be a mix of old and new until it ends. | | `SUCCEEDED` | The last import wrote its last batch; `last_successful_import_at` is its date. | | `FAILED` | The last import stopped on an error. Batches written before it stopped are kept, so the stored guide may be partly refreshed. | | `INTERRUPTED` | The last import was still running when the server released a synchronisation stuck for too long — a crash or a restart. Same reading as `FAILED`. |  A client shows a running, failed or interrupted attempt **before** a recent success date, never behind it: \"the last import did not finish\" is the sentence that matters, even when the one before it finished an hour ago. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public enum EpgAttemptStatus {
  
  UNKNOWN("UNKNOWN"),
  
  RUNNING("RUNNING"),
  
  SUCCEEDED("SUCCEEDED"),
  
  FAILED("FAILED"),
  
  INTERRUPTED("INTERRUPTED");

  private final String value;

  EpgAttemptStatus(String value) {
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
  public static EpgAttemptStatus fromValue(String value) {
    for (EpgAttemptStatus b : EpgAttemptStatus.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

