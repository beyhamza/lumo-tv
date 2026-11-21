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
 * `PRIVACY_POLICY` is the policy Google Play links to. `TERMS_OF_USE` are the CGU, `TERMS_OF_SALE` the CGV. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public enum LegalDocumentType {
  
  PRIVACY_POLICY("PRIVACY_POLICY"),
  
  TERMS_OF_USE("TERMS_OF_USE"),
  
  TERMS_OF_SALE("TERMS_OF_SALE");

  private final String value;

  LegalDocumentType(String value) {
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
  public static LegalDocumentType fromValue(String value) {
    for (LegalDocumentType b : LegalDocumentType.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

