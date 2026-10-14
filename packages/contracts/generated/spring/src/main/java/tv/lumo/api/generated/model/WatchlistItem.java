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
import tv.lumo.api.generated.model.WatchlistContentType;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * One film or series in the list.  Carries a display snapshot (&#x60;title&#x60;, &#x60;poster_url&#x60;) so the card renders when the catalogue no longer has the content (&#x60;adr/0011&#x60;, ruling 3). While the content is available, both are the catalogue&#39;s current values; once it is gone, they are the values recorded when the item was added. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class WatchlistItem {

  private UUID id;

  private UUID sourceId;

  private WatchlistContentType contentType;

  private @Nullable UUID contentId = null;

  private Boolean available;

  private String title;

  private @Nullable String posterUrl = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime addedAt;

  public WatchlistItem() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public WatchlistItem(UUID id, UUID sourceId, WatchlistContentType contentType, Boolean available, String title, OffsetDateTime addedAt) {
    this.id = id;
    this.sourceId = sourceId;
    this.contentType = contentType;
    this.available = available;
    this.title = title;
    this.addedAt = addedAt;
  }

  public WatchlistItem id(UUID id) {
    this.id = id;
    return this;
  }

  /**
   * The item's own identifier — what a removal addresses.
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

  public WatchlistItem sourceId(UUID sourceId) {
    this.sourceId = sourceId;
    return this;
  }

  /**
   * Get sourceId
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

  public WatchlistItem contentType(WatchlistContentType contentType) {
    this.contentType = contentType;
    return this;
  }

  /**
   * Get contentType
   * @return contentType
   */
  @NotNull @Valid 
  @JsonProperty("content_type")
  public WatchlistContentType getContentType() {
    return contentType;
  }

  public void setContentType(WatchlistContentType contentType) {
    this.contentType = contentType;
  }

  public WatchlistItem contentId(@Nullable UUID contentId) {
    this.contentId = contentId;
    return this;
  }

  /**
   * `VodItem.id` or `Series.id`, to open the sheet. Null exactly when `available` is false.  Resolved on every read rather than stored: a film that disappears and comes back gets a new catalogue id, and the item must follow it (`WL-15`). 
   * @return contentId
   */
  @Valid 
  @JsonProperty("content_id")
  public @Nullable UUID getContentId() {
    return contentId;
  }

  public void setContentId(@Nullable UUID contentId) {
    this.contentId = contentId;
  }

  public WatchlistItem available(Boolean available) {
    this.available = available;
    return this;
  }

  /**
   * False only after a synchronisation of the source **completed** and no longer listed the content (`WL-01`). A failed or interrupted synchronisation keeps the previous catalogue, so it never makes an item unavailable, and neither does a network error on the client (`WL-04`). 
   * @return available
   */
  @NotNull 
  @JsonProperty("available")
  public Boolean getAvailable() {
    return available;
  }

  public void setAvailable(Boolean available) {
    this.available = available;
  }

  public WatchlistItem title(String title) {
    this.title = title;
    return this;
  }

  /**
   * Get title
   * @return title
   */
  @NotNull 
  @JsonProperty("title")
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public WatchlistItem posterUrl(@Nullable String posterUrl) {
    this.posterUrl = posterUrl;
    return this;
  }

  /**
   * As served by the source. May stop loading, especially once `available` is false; clients show a placeholder then. 
   * @return posterUrl
   */
  
  @JsonProperty("poster_url")
  public @Nullable String getPosterUrl() {
    return posterUrl;
  }

  public void setPosterUrl(@Nullable String posterUrl) {
    this.posterUrl = posterUrl;
  }

  public WatchlistItem addedAt(OffsetDateTime addedAt) {
    this.addedAt = addedAt;
    return this;
  }

  /**
   * When the server accepted the add. The list's order. Never changed by a replay, a second add, playback, or the content disappearing and coming back. 
   * @return addedAt
   */
  @NotNull @Valid 
  @JsonProperty("added_at")
  public OffsetDateTime getAddedAt() {
    return addedAt;
  }

  public void setAddedAt(OffsetDateTime addedAt) {
    this.addedAt = addedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    WatchlistItem watchlistItem = (WatchlistItem) o;
    return Objects.equals(this.id, watchlistItem.id) &&
        Objects.equals(this.sourceId, watchlistItem.sourceId) &&
        Objects.equals(this.contentType, watchlistItem.contentType) &&
        Objects.equals(this.contentId, watchlistItem.contentId) &&
        Objects.equals(this.available, watchlistItem.available) &&
        Objects.equals(this.title, watchlistItem.title) &&
        Objects.equals(this.posterUrl, watchlistItem.posterUrl) &&
        Objects.equals(this.addedAt, watchlistItem.addedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, sourceId, contentType, contentId, available, title, posterUrl, addedAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class WatchlistItem {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    sourceId: ").append(toIndentedString(sourceId)).append("\n");
    sb.append("    contentType: ").append(toIndentedString(contentType)).append("\n");
    sb.append("    contentId: ").append(toIndentedString(contentId)).append("\n");
    sb.append("    available: ").append(toIndentedString(available)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    posterUrl: ").append(toIndentedString(posterUrl)).append("\n");
    sb.append("    addedAt: ").append(toIndentedString(addedAt)).append("\n");
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

