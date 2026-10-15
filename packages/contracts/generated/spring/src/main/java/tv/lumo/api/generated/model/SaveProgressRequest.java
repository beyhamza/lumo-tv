package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.ProgressItemType;
import tv.lumo.api.generated.model.WatchedSegment;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Upsert keyed on &#x60;(source_id, item_type, item_ref)&#x60; for the caller.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class SaveProgressRequest {

  private UUID sourceId;

  private ProgressItemType itemType;

  private String itemRef;

  private Long positionMs;

  private @Nullable Long durationMs = null;

  @Valid
  private List<@Valid WatchedSegment> watchedSegments = new ArrayList<>();

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private @Nullable OffsetDateTime observedAt;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private @Nullable OffsetDateTime sessionStartedAt;

  public SaveProgressRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public SaveProgressRequest(UUID sourceId, ProgressItemType itemType, String itemRef, Long positionMs) {
    this.sourceId = sourceId;
    this.itemType = itemType;
    this.itemRef = itemRef;
    this.positionMs = positionMs;
  }

  public SaveProgressRequest sourceId(UUID sourceId) {
    this.sourceId = sourceId;
    return this;
  }

  /**
   * The source the item belongs to, and **part of the key**.  The key is `(source_id, item_type, item_ref)` because `item_ref` is declared opaque: nothing in this API constrains what a client puts there, and two subscriptions using `1042` for two different films would otherwise collide. The bug would look like a film mysteriously resuming twenty minutes in.  **What clients actually send is one of our own identifiers** — see `item_ref` below — which makes this field redundant for both types and not for the schema. It stays required: a required field that is sometimes redundant is cheaper than a key that changes shape, and it is what lets a client filter a page of progress down to one source without resolving every row first.  The alternative was a convention — prefix `item_ref` with the source id — and it was refused: a convention is a rule three clients have to apply identically, and one of them getting it wrong produces exactly the collision this field prevents, silently. 
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

  public SaveProgressRequest itemType(ProgressItemType itemType) {
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

  public SaveProgressRequest itemRef(String itemRef) {
    this.itemRef = itemRef;
    return this;
  }

  /**
   * Identifier of the item, opaque to this endpoint — it is stored and compared, never joined on.  **For `VOD`, send `VodItem.id`.** That was decided when the resume rail was built (`S5-11`) and it is worth stating rather than leaving to each client: a \"continue watching\" rail has to turn these rows back into films with posters and titles, and `GET /sources/{id}/vod?ids=` is the only operation that does. A panel-minted reference would have needed a lookup operation of its own, for no difference anybody could see.  It costs nothing in stability: `vod_item` is upserted on `(source_id, external_id)`, so a row keeps its id across re-synchronisations — which is the property a panel-minted reference would have been chosen for.  **For `EPISODE`, send `Episode.id`** — decided in `S6-01`, on the same argument and with one more step to resolve.  A \"continue watching\" rail for series shows **series**, not episodes: nobody remembers an episode identifier, they remember having got to episode four. So a saved position has to resolve to a series *and* to a place in it. `GET /sources/{id}/episodes?ids=` turns these rows into episodes carrying `series_id`, `season_number` and `episode_number`; `GET /sources/{id}/series?ids=` turns those into posters and titles. Two requests for a whole rail, not two per row.  That resolver exists **because of this field**, and it was added when this decision was taken rather than discovered when the rail was built — which is what happened for films in sprint 5.  The stability argument is the film one: `episode` is upserted on `(series_id, external_id)` — the panel's own episode identifier, which unlike a season's always exists because it is what the playback URL is built from. A row therefore keeps its id across re-synchronisations and across the tree being refetched when its cache expires. 
   * @return itemRef
   */
  @NotNull @Size(min = 1, max = 200) 
  @JsonProperty("item_ref")
  public String getItemRef() {
    return itemRef;
  }

  public void setItemRef(String itemRef) {
    this.itemRef = itemRef;
  }

  public SaveProgressRequest positionMs(Long positionMs) {
    this.positionMs = positionMs;
    return this;
  }

  /**
   * Get positionMs
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

  public SaveProgressRequest durationMs(@Nullable Long durationMs) {
    this.durationMs = durationMs;
    return this;
  }

  /**
   * Total duration, when the source states one. Sent so a client can decide an item is finished without a second lookup.  **Finished is a threshold, not an event**: past 95 % of the duration an item leaves the \"continue watching\" rail. With no duration — and many panels give none — it never leaves, which is the right default: a film that lingers in the rail is an annoyance, a film that vanishes before the end is a loss. 
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

  public SaveProgressRequest watchedSegments(List<@Valid WatchedSegment> watchedSegments) {
    this.watchedSegments = watchedSegments;
    return this;
  }

  public SaveProgressRequest addWatchedSegmentsItem(WatchedSegment watchedSegmentsItem) {
    if (this.watchedSegments == null) {
      this.watchedSegments = new ArrayList<>();
    }
    this.watchedSegments.add(watchedSegmentsItem);
    return this;
  }

  /**
   * Intervals of actual playback — not paused, not buffering, not seeking — since the previous save, in wall-clock time. Seeking produces no segment, so jumping ahead adds no watched time (CW-04). Intervals in the future are clamped to the server's clock. 
   * @return watchedSegments
   */
  @Valid @Size(max = 20) 
  @JsonProperty("watched_segments")
  public List<@Valid WatchedSegment> getWatchedSegments() {
    return watchedSegments;
  }

  public void setWatchedSegments(List<@Valid WatchedSegment> watchedSegments) {
    this.watchedSegments = watchedSegments;
  }

  public SaveProgressRequest observedAt(@Nullable OffsetDateTime observedAt) {
    this.observedAt = observedAt;
    return this;
  }

  /**
   * When the device read `position_ms`. The position with the latest `observed_at` wins; an older one arriving late, from a device that reconnects, does not overwrite it (CW-32). Omitted: the receive time. 
   * @return observedAt
   */
  @Valid 
  @JsonProperty("observed_at")
  public @Nullable OffsetDateTime getObservedAt() {
    return observedAt;
  }

  public void setObservedAt(@Nullable OffsetDateTime observedAt) {
    this.observedAt = observedAt;
  }

  public SaveProgressRequest sessionStartedAt(@Nullable OffsetDateTime sessionStartedAt) {
    this.sessionStartedAt = sessionStartedAt;
    return this;
  }

  /**
   * When playback really started on this device for this session — the first frame, not the sheet opening. See `POST /me/progress/hide`. 
   * @return sessionStartedAt
   */
  @Valid 
  @JsonProperty("session_started_at")
  public @Nullable OffsetDateTime getSessionStartedAt() {
    return sessionStartedAt;
  }

  public void setSessionStartedAt(@Nullable OffsetDateTime sessionStartedAt) {
    this.sessionStartedAt = sessionStartedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SaveProgressRequest saveProgressRequest = (SaveProgressRequest) o;
    return Objects.equals(this.sourceId, saveProgressRequest.sourceId) &&
        Objects.equals(this.itemType, saveProgressRequest.itemType) &&
        Objects.equals(this.itemRef, saveProgressRequest.itemRef) &&
        Objects.equals(this.positionMs, saveProgressRequest.positionMs) &&
        Objects.equals(this.durationMs, saveProgressRequest.durationMs) &&
        Objects.equals(this.watchedSegments, saveProgressRequest.watchedSegments) &&
        Objects.equals(this.observedAt, saveProgressRequest.observedAt) &&
        Objects.equals(this.sessionStartedAt, saveProgressRequest.sessionStartedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceId, itemType, itemRef, positionMs, durationMs, watchedSegments, observedAt, sessionStartedAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SaveProgressRequest {\n");
    sb.append("    sourceId: ").append(toIndentedString(sourceId)).append("\n");
    sb.append("    itemType: ").append(toIndentedString(itemType)).append("\n");
    sb.append("    itemRef: ").append(toIndentedString(itemRef)).append("\n");
    sb.append("    positionMs: ").append(toIndentedString(positionMs)).append("\n");
    sb.append("    durationMs: ").append(toIndentedString(durationMs)).append("\n");
    sb.append("    watchedSegments: ").append(toIndentedString(watchedSegments)).append("\n");
    sb.append("    observedAt: ").append(toIndentedString(observedAt)).append("\n");
    sb.append("    sessionStartedAt: ").append(toIndentedString(sessionStartedAt)).append("\n");
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

