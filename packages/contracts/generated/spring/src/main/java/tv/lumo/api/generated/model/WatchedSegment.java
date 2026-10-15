package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * One interval of actual playback, in wall-clock time.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class WatchedSegment {

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime startedAt;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime endedAt;

  public WatchedSegment() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public WatchedSegment(OffsetDateTime startedAt, OffsetDateTime endedAt) {
    this.startedAt = startedAt;
    this.endedAt = endedAt;
  }

  public WatchedSegment startedAt(OffsetDateTime startedAt) {
    this.startedAt = startedAt;
    return this;
  }

  /**
   * Get startedAt
   * @return startedAt
   */
  @NotNull @Valid 
  @JsonProperty("started_at")
  public OffsetDateTime getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(OffsetDateTime startedAt) {
    this.startedAt = startedAt;
  }

  public WatchedSegment endedAt(OffsetDateTime endedAt) {
    this.endedAt = endedAt;
    return this;
  }

  /**
   * Not before `started_at`.
   * @return endedAt
   */
  @NotNull @Valid 
  @JsonProperty("ended_at")
  public OffsetDateTime getEndedAt() {
    return endedAt;
  }

  public void setEndedAt(OffsetDateTime endedAt) {
    this.endedAt = endedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    WatchedSegment watchedSegment = (WatchedSegment) o;
    return Objects.equals(this.startedAt, watchedSegment.startedAt) &&
        Objects.equals(this.endedAt, watchedSegment.endedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(startedAt, endedAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class WatchedSegment {\n");
    sb.append("    startedAt: ").append(toIndentedString(startedAt)).append("\n");
    sb.append("    endedAt: ").append(toIndentedString(endedAt)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

