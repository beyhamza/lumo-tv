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
 * **Complete enumeration of ingestion error codes for v1.**  These are the values that reach a user during onboarding, whether synchronously in a `422` `Problem.code` or asynchronously in `Source.error_code`. Each one must have an actionable FR and EN message in every client — the whole point of this enumeration is that no client ever falls back to a generic failure message here.  | Value | Cause | What the user should be told | |---|---|---| | `SOURCE_UNREACHABLE` | The host did not answer, DNS failed, or the request timed out. | The server could not be reached. Offer \"Try again\". Must read as clearly different from a credential refusal. | | `SOURCE_AUTH_FAILED` | The panel rejected `username`/`password`. | The credentials were refused by the server. Keep the host in the form so only what is wrong is retyped. | | `SOURCE_EXPIRED` | The Xtream account itself has expired. | The subscription with the provider has expired; Lumo cannot renew it. | | `SOURCE_MAX_CONNECTIONS` | The panel reports the simultaneous-connection limit reached. | The subscription limits simultaneous streams; close another stream. | | `SOURCE_INVALID_FORMAT` | The response is not a valid M3U playlist, XMLTV guide or Xtream payload. | The URL does not return a playlist. Explain what a correct M3U URL looks like. | | `SOURCE_EMPTY` | Parsing succeeded but yielded zero channels. | The playlist is empty. Never show a success screen over an empty list. | | `SOURCE_TOO_LARGE` | The payload exceeds the ingestion size ceiling. | The playlist is too large to import. | 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public enum IngestionErrorCode {
  
  SOURCE_UNREACHABLE("SOURCE_UNREACHABLE"),
  
  SOURCE_AUTH_FAILED("SOURCE_AUTH_FAILED"),
  
  SOURCE_EXPIRED("SOURCE_EXPIRED"),
  
  SOURCE_MAX_CONNECTIONS("SOURCE_MAX_CONNECTIONS"),
  
  SOURCE_INVALID_FORMAT("SOURCE_INVALID_FORMAT"),
  
  SOURCE_EMPTY("SOURCE_EMPTY"),
  
  SOURCE_TOO_LARGE("SOURCE_TOO_LARGE");

  private final String value;

  IngestionErrorCode(String value) {
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
  public static IngestionErrorCode fromValue(String value) {
    for (IngestionErrorCode b : IngestionErrorCode.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

