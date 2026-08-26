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
 * State of the entitlement. `ACTIVE` and `TRIALING` grant premium features; the other three do not.  `TRIALING` is a separate state rather than a flag on `ACTIVE` because the two produce different screens. \"Your trial ends in 3 days\" is an invitation to enter a card; \"renews on the 14th\" is a reassurance. Told apart only by `plan` and `status`, they would be indistinguishable, and every client would have to guess from `current_period_end`. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public enum EntitlementStatus {
  
  ACTIVE("ACTIVE"),
  
  TRIALING("TRIALING"),
  
  PAST_DUE("PAST_DUE"),
  
  CANCELED("CANCELED"),
  
  EXPIRED("EXPIRED");

  private final String value;

  EntitlementStatus(String value) {
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
  public static EntitlementStatus fromValue(String value) {
    for (EntitlementStatus b : EntitlementStatus.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

