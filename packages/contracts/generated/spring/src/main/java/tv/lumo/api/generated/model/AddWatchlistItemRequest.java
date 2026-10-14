package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.UUID;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.WatchlistContentType;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * &#x60;source_id&#x60; is not accepted: it is derived from the content, exactly as on &#x60;AddFavoriteRequest&#x60;. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class AddWatchlistItemRequest {

  private WatchlistContentType contentType;

  private UUID contentId;

  public AddWatchlistItemRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public AddWatchlistItemRequest(WatchlistContentType contentType, UUID contentId) {
    this.contentType = contentType;
    this.contentId = contentId;
  }

  public AddWatchlistItemRequest contentType(WatchlistContentType contentType) {
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

  public AddWatchlistItemRequest contentId(UUID contentId) {
    this.contentId = contentId;
    return this;
  }

  /**
   * `VodItem.id` for `VOD`, `Series.id` for `SERIES`.
   * @return contentId
   */
  @NotNull @Valid 
  @JsonProperty("content_id")
  public UUID getContentId() {
    return contentId;
  }

  public void setContentId(UUID contentId) {
    this.contentId = contentId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AddWatchlistItemRequest addWatchlistItemRequest = (AddWatchlistItemRequest) o;
    return Objects.equals(this.contentType, addWatchlistItemRequest.contentType) &&
        Objects.equals(this.contentId, addWatchlistItemRequest.contentId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(contentType, contentId);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AddWatchlistItemRequest {\n");
    sb.append("    contentType: ").append(toIndentedString(contentType)).append("\n");
    sb.append("    contentId: ").append(toIndentedString(contentId)).append("\n");
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

