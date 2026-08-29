package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.ProgressItemType;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Where the user stopped watching a VOD item or an episode.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class PlaybackProgress {

  private UUID id;

  private UUID sourceId;

  private ProgressItemType itemType;

  private String itemRef;

  private Long positionMs;

  private @Nullable Long durationMs = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime updatedAt;

  public PlaybackProgress() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public PlaybackProgress(UUID id, UUID sourceId, ProgressItemType itemType, String itemRef, Long positionMs, OffsetDateTime updatedAt) {
    this.id = id;
    this.sourceId = sourceId;
    this.itemType = itemType;
    this.itemRef = itemRef;
    this.positionMs = positionMs;
    this.updatedAt = updatedAt;
  }

  public PlaybackProgress id(UUID id) {
    this.id = id;
    return this;
  }

  /**
   * Get id
   * @return id
   */
  @NotNull @Valid 
  @JsonProperty("id")
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public PlaybackProgress sourceId(UUID sourceId) {
    this.sourceId = sourceId;
    return this;
  }

  /**
   * The source this position belongs to. Returned because it is part of the key: a client reading a page of progress has to be able to tell two subscriptions' `1042` apart, exactly as the server does. 
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

  public PlaybackProgress itemType(ProgressItemType itemType) {
    this.itemType = itemType;
    return this;
  }

  /**
   * Get itemType
   * @return itemType
   */
  @NotNull @Valid 
  @JsonProperty("item_type")
  public ProgressItemType getItemType() {
    return itemType;
  }

  public void setItemType(ProgressItemType itemType) {
    this.itemType = itemType;
  }

  public PlaybackProgress itemRef(String itemRef) {
    this.itemRef = itemRef;
    return this;
  }

  /**
   * Identifier of the item, opaque to this API. For `VOD` it is `VodItem.id` — see `SaveProgressRequest.item_ref`, where that is argued — which is what lets a \"continue watching\" rail resolve these rows through `GET /sources/{id}/vod?ids=`. 
   * @return itemRef
   */
  @NotNull 
  @JsonProperty("item_ref")
  public String getItemRef() {
    return itemRef;
  }

  public void setItemRef(String itemRef) {
    this.itemRef = itemRef;
  }

  public PlaybackProgress positionMs(Long positionMs) {
    this.positionMs = positionMs;
    return this;
  }

  /**
   * Playback position, in milliseconds.
   * minimum: 0
   * @return positionMs
   */
  @NotNull @Min(0L) 
  @JsonProperty("position_ms")
  public Long getPositionMs() {
    return positionMs;
  }

  public void setPositionMs(Long positionMs) {
    this.positionMs = positionMs;
  }

  public PlaybackProgress durationMs(@Nullable Long durationMs) {
    this.durationMs = durationMs;
    return this;
  }

  /**
   * Total duration, when known.
   * minimum: 0
   * @return durationMs
   */
  @Min(0L) 
  @JsonProperty("duration_ms")
  public @Nullable Long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(@Nullable Long durationMs) {
    this.durationMs = durationMs;
  }

  public PlaybackProgress updatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  /**
   * Get updatedAt
   * @return updatedAt
   */
  @NotNull @Valid 
  @JsonProperty("updated_at")
  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PlaybackProgress playbackProgress = (PlaybackProgress) o;
    return Objects.equals(this.id, playbackProgress.id) &&
        Objects.equals(this.sourceId, playbackProgress.sourceId) &&
        Objects.equals(this.itemType, playbackProgress.itemType) &&
        Objects.equals(this.itemRef, playbackProgress.itemRef) &&
        Objects.equals(this.positionMs, playbackProgress.positionMs) &&
        Objects.equals(this.durationMs, playbackProgress.durationMs) &&
        Objects.equals(this.updatedAt, playbackProgress.updatedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, sourceId, itemType, itemRef, positionMs, durationMs, updatedAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PlaybackProgress {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    sourceId: ").append(toIndentedString(sourceId)).append("\n");
    sb.append("    itemType: ").append(toIndentedString(itemType)).append("\n");
    sb.append("    itemRef: ").append(toIndentedString(itemRef)).append("\n");
    sb.append("    positionMs: ").append(toIndentedString(positionMs)).append("\n");
    sb.append("    durationMs: ").append(toIndentedString(durationMs)).append("\n");
    sb.append("    updatedAt: ").append(toIndentedString(updatedAt)).append("\n");
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

