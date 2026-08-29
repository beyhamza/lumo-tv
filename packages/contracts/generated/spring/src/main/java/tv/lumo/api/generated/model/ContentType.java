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
 * Kind of catalogue a `category` groups.  All three are now ingested and served, each with its own listing and its own playback operation. `SERIES` was the last to arrive; until sprint 6 the value existed so the schema was complete and nothing produced it.  An earlier version of this description claimed all three were ingested when only `LIVE` was. That was corrected rather than left standing: a false sentence in the document that decides what the server does costs more than an absent one — and the sentence above is written the day the third one became true, not the day it was planned.  **`SERIES` is only ever produced by an Xtream source** (`adr/0010`). A playlist declares no season and no episode, and this API does not reconstruct a tree from titles: an M3U entry that looks like an episode is classified by `adr/0009` like anything else, and stays a film or a channel.  How an entry becomes `VOD` differs by source, and only one of the two cases is a judgement call. An Xtream panel answers it itself, through `get_vod_streams` beside `get_live_streams`. An M3U playlist declares nothing, so the type is inferred — from the URL alone, and never from what the entry is called. The rule, what it costs when it is wrong, and which way its doubt falls are `adr/0009`. 
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

