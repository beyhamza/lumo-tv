package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.UUID;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * A live channel.  **&#x60;stream_url&#x60; is deliberately absent.** It is a sensitive value and is emitted only by &#x60;GET /channels/{id}/playback&#x60;, one channel at a time, after an ownership check. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class Channel {

  private UUID id;

  private UUID sourceId;

  private @Nullable UUID categoryId = null;

  private @Nullable String externalId = null;

  private String name;

  private @Nullable String logoUrl = null;

  private @Nullable String tvgId = null;

  private Integer position;

  private Boolean isAdult;

  public Channel() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public Channel(UUID id, UUID sourceId, String name, Integer position, Boolean isAdult) {
    this.id = id;
    this.sourceId = sourceId;
    this.name = name;
    this.position = position;
    this.isAdult = isAdult;
  }

  public Channel id(UUID id) {
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

  public Channel sourceId(UUID sourceId) {
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

  public Channel categoryId(@Nullable UUID categoryId) {
    this.categoryId = categoryId;
    return this;
  }

  /**
   * Get categoryId
   * @return categoryId
   */
  @Valid 
  @JsonProperty("category_id")
  public @Nullable UUID getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(@Nullable UUID categoryId) {
    this.categoryId = categoryId;
  }

  public Channel externalId(@Nullable String externalId) {
    this.externalId = externalId;
    return this;
  }

  /**
   * Get externalId
   * @return externalId
   */
  
  @JsonProperty("external_id")
  public @Nullable String getExternalId() {
    return externalId;
  }

  public void setExternalId(@Nullable String externalId) {
    this.externalId = externalId;
  }

  public Channel name(String name) {
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

  public Channel logoUrl(@Nullable String logoUrl) {
    this.logoUrl = logoUrl;
    return this;
  }

  /**
   * Logo advertised by the user's own playlist (`tvg-logo`). Lumo ships no bundled logo and no fallback artwork of its own. 
   * @return logoUrl
   */
  
  @JsonProperty("logo_url")
  public @Nullable String getLogoUrl() {
    return logoUrl;
  }

  public void setLogoUrl(@Nullable String logoUrl) {
    this.logoUrl = logoUrl;
  }

  public Channel tvgId(@Nullable String tvgId) {
    this.tvgId = tvgId;
    return this;
  }

  /**
   * EPG identifier, used to join with `epg_programme`.
   * @return tvgId
   */
  
  @JsonProperty("tvg_id")
  public @Nullable String getTvgId() {
    return tvgId;
  }

  public void setTvgId(@Nullable String tvgId) {
    this.tvgId = tvgId;
  }

  public Channel position(Integer position) {
    this.position = position;
    return this;
  }

  /**
   * Get position
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

  public Channel isAdult(Boolean isAdult) {
    this.isAdult = isAdult;
    return this;
  }

  /**
   * Flagged by the source as adult content.
   * @return isAdult
   */
  @NotNull 
  @JsonProperty("is_adult")
  public Boolean getIsAdult() {
    return isAdult;
  }

  public void setIsAdult(Boolean isAdult) {
    this.isAdult = isAdult;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Channel channel = (Channel) o;
    return Objects.equals(this.id, channel.id) &&
        Objects.equals(this.sourceId, channel.sourceId) &&
        Objects.equals(this.categoryId, channel.categoryId) &&
        Objects.equals(this.externalId, channel.externalId) &&
        Objects.equals(this.name, channel.name) &&
        Objects.equals(this.logoUrl, channel.logoUrl) &&
        Objects.equals(this.tvgId, channel.tvgId) &&
        Objects.equals(this.position, channel.position) &&
        Objects.equals(this.isAdult, channel.isAdult);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, sourceId, categoryId, externalId, name, logoUrl, tvgId, position, isAdult);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Channel {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    sourceId: ").append(toIndentedString(sourceId)).append("\n");
    sb.append("    categoryId: ").append(toIndentedString(categoryId)).append("\n");
    sb.append("    externalId: ").append(toIndentedString(externalId)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    logoUrl: ").append(toIndentedString(logoUrl)).append("\n");
    sb.append("    tvgId: ").append(toIndentedString(tvgId)).append("\n");
    sb.append("    position: ").append(toIndentedString(position)).append("\n");
    sb.append("    isAdult: ").append(toIndentedString(isAdult)).append("\n");
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

