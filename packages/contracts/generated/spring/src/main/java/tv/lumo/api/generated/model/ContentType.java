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
 * Kind of catalogue a `category` groups.  `LIVE` and `VOD` are ingested and served, each with its own listing and its own playback operation. `SERIES` is **accepted by this enumeration and produced by nothing**: the column and the value exist so the schema is complete, and the ingestion that would fill them is sprint 6.  An earlier version of this description claimed all three were ingested. They were not — only `LIVE` was — and a false sentence in the document that decides what the server does costs more than an absent one.  How an entry becomes `VOD` differs by source, and only one of the two cases is a judgement call. An Xtream panel answers it itself, through `get_vod_streams` beside `get_live_streams`. An M3U playlist declares nothing, so the type is inferred — from the URL alone, and never from what the entry is called. The rule, what it costs when it is wrong, and which way its doubt falls are `adr/0009`. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public enum ContentType {
  
  LIVE("LIVE"),
  
  VOD("VOD"),
  
  SERIES("SERIES");

  private final String value;

  ContentType(String value) {
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
  public static ContentType fromValue(String value) {
    for (ContentType b : ContentType.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

