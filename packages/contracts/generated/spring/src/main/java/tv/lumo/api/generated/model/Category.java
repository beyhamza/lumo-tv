package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.UUID;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.ContentType;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * A grouping of channels within a source. Channels carrying no &#x60;group-title&#x60; in the playlist land in a generated \&quot;Unclassified\&quot; category rather than being dropped. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class Category {

  private UUID id;

  private UUID sourceId;

  private @Nullable String externalId = null;

  private String name;

  private ContentType contentType;

  private Integer position;

  private @Nullable Integer channelCount = null;

  public Category() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public Category(UUID id, UUID sourceId, String name, ContentType contentType, Integer position) {
    this.id = id;
    this.sourceId = sourceId;
    this.name = name;
    this.contentType = contentType;
    this.position = position;
  }

  public Category id(UUID id) {
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

  public Category sourceId(UUID sourceId) {
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

  public Category externalId(@Nullable String externalId) {
    this.externalId = externalId;
    return this;
  }

  /**
   * Identifier used by the origin panel or playlist.  One value is assigned by Lumo rather than by the source: `m3u:__unclassified__` is the group that collects M3U entries with no `group-title` (US-07). It is the only category the server invents, so it is the only one whose `name` is not the user's own wording — clients render their own translation when they see this identifier and fall back to `name` otherwise. 
   * @return externalId
   */
  
  @JsonProperty("external_id")
  public @Nullable String getExternalId() {
    return externalId;
  }

  public void setExternalId(@Nullable String externalId) {
    this.externalId = externalId;
  }

  public Category name(String name) {
    this.name = name;
    return this;
  }

  /**
   * Get name
   * @return name
   */
  @NotNull 
  @JsonProperty("name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Category contentType(ContentType contentType) {
    this.contentType = contentType;
    return this;
  }

  /**
   * Get contentType
   * @return contentType
   */
  @NotNull @Valid 
  @JsonProperty("content_type")
  public ContentType getContentType() {
    return contentType;
  }

  public void setContentType(ContentType contentType) {
    this.contentType = contentType;
  }

  public Category position(Integer position) {
    this.position = position;
    return this;
  }

  /**
   * Display order within the source.
   * @return position
   */
  @NotNull 
  @JsonProperty("position")
  public Integer getPosition() {
    return position;
  }

  public void setPosition(Integer position) {
    this.position = position;
  }

  public Category channelCount(@Nullable Integer channelCount) {
    this.channelCount = channelCount;
    return this;
  }

  /**
   * Items in this category — channels, films or series, according to `content_type`. Derived, not stored on the entity. Renders \"N\" next to each category (US-08). The name predates the film and series catalogues and is kept: renaming a field is a breaking change for three clients, a description is not. 
   * @return channelCount
   */
  
  @JsonProperty("channel_count")
  public @Nullable Integer getChannelCount() {
    return channelCount;
  }

  public void setChannelCount(@Nullable Integer channelCount) {
    this.channelCount = channelCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Category category = (Category) o;
    return Objects.equals(this.id, category.id) &&
        Objects.equals(this.sourceId, category.sourceId) &&
        Objects.equals(this.externalId, category.externalId) &&
        Objects.equals(this.name, category.name) &&
        Objects.equals(this.contentType, category.contentType) &&
        Objects.equals(this.position, category.position) &&
        Objects.equals(this.channelCount, category.channelCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, sourceId, externalId, name, contentType, position, channelCount);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Category {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    sourceId: ").append(toIndentedString(sourceId)).append("\n");
    sb.append("    externalId: ").append(toIndentedString(externalId)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    contentType: ").append(toIndentedString(contentType)).append("\n");
    sb.append("    position: ").append(toIndentedString(position)).append("\n");
    sb.append("    channelCount: ").append(toIndentedString(channelCount)).append("\n");
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

