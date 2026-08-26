package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * One live channel the user watched, and when.  Carries identifiers and nothing else: the name, the logo and the current programme are already reachable from the catalogue and the guide, and denormalising them here would mean a rail showing a channel name that the last ingestion has since changed. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class RecentChannel {

  private UUID channelId;

  private UUID sourceId;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime watchedAt;

  public RecentChannel() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public RecentChannel(UUID channelId, UUID sourceId, OffsetDateTime watchedAt) {
    this.channelId = channelId;
    this.sourceId = sourceId;
    this.watchedAt = watchedAt;
  }

  public RecentChannel channelId(UUID channelId) {
    this.channelId = channelId;
    return this;
  }

  /**
   * Get channelId
   * @return channelId
   */
  @NotNull @Valid 
  @JsonProperty("channel_id")
  public UUID getChannelId() {
    return channelId;
  }

  public void setChannelId(UUID channelId) {
    this.channelId = channelId;
  }

  public RecentChannel sourceId(UUID sourceId) {
    this.sourceId = sourceId;
    return this;
  }

  /**
   * Derived from the channel, so the two cannot disagree.
   * @return sourceId
   */
  @NotNull @Valid 
  @JsonProperty("source_id")
  public UUID getSourceId() {
    return sourceId;
  }

  public void setSourceId(UUID sourceId) {
    this.sourceId = sourceId;
  }

  public RecentChannel watchedAt(OffsetDateTime watchedAt) {
    this.watchedAt = watchedAt;
    return this;
  }

  /**
   * Last time playback started on this channel.
   * @return watchedAt
   */
  @NotNull @Valid 
  @JsonProperty("watched_at")
  public OffsetDateTime getWatchedAt() {
    return watchedAt;
  }

  public void setWatchedAt(OffsetDateTime watchedAt) {
    this.watchedAt = watchedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RecentChannel recentChannel = (RecentChannel) o;
    return Objects.equals(this.channelId, recentChannel.channelId) &&
        Objects.equals(this.sourceId, recentChannel.sourceId) &&
        Objects.equals(this.watchedAt, recentChannel.watchedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(channelId, sourceId, watchedAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class RecentChannel {\n");
    sb.append("    channelId: ").append(toIndentedString(channelId)).append("\n");
    sb.append("    sourceId: ").append(toIndentedString(sourceId)).append("\n");
    sb.append("    watchedAt: ").append(toIndentedString(watchedAt)).append("\n");
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

