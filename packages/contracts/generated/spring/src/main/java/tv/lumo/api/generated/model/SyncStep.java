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
 * Where a running ingestion has got to.  `SourceStatus` says whether ingestion is running; this says how far it is. Onboarding waits here — a large playlist takes up to a minute — and a minute of silence is where a user concludes it is broken and closes the application. A named step says two things an indeterminate spinner cannot: that it is moving, and how far it got when it fails.  | Value | Cause | |---|---| | `CONNECTING` | Opening the connection to the user's server. | | `AUTHENTICATED` | Credentials accepted; nothing parsed yet. | | `PARSING_CHANNELS` | Reading the live channels. | | `PARSING_VOD` | Reading the film catalogue, when the source has one. | | `FETCHING_EPG` | Retrieving the XMLTV guide, when the source has one. |  These are the server's real phases and must stay so. A step the implementation does not actually distinguish is a reassuring fiction, and three true steps beat four invented ones.  `PARSING_VOD` earns its place by that rule rather than in spite of it: a film catalogue is commonly three times the size of the channel list, so an ingestion that stayed on `PARSING_CHANNELS` throughout would leave the waiting screen still and silent for the longest minute of the import — which is where somebody decides the application is broken and closes it. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public enum SyncStep {
  
  CONNECTING("CONNECTING"),
  
  AUTHENTICATED("AUTHENTICATED"),
  
  PARSING_CHANNELS("PARSING_CHANNELS"),
  
  PARSING_VOD("PARSING_VOD"),
  
  FETCHING_EPG("FETCHING_EPG");

  private final String value;

  SyncStep(String value) {
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
  public static SyncStep fromValue(String value) {
    for (SyncStep b : SyncStep.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

