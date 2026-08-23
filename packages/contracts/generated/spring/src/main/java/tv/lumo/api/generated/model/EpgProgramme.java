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
 * One programme from the guide.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class EpgProgramme {

  private UUID id;

  private UUID sourceId;

  private String tvgId;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime startsAt;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime endsAt;

  private String title;

  private @Nullable String description = null;

  private @Nullable String category = null;

  public EpgProgramme() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public EpgProgramme(UUID id, UUID sourceId, String tvgId, OffsetDateTime startsAt, OffsetDateTime endsAt, String title) {
    this.id = id;
    this.sourceId = sourceId;
    this.tvgId = tvgId;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.title = title;
  }

  public EpgProgramme id(UUID id) {
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

  public EpgProgramme sourceId(UUID sourceId) {
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

  public EpgProgramme tvgId(String tvgId) {
    this.tvgId = tvgId;
    return this;
  }

  /**
   * Get tvgId
   * @return tvgId
   */
  @NotNull 
  @JsonProperty("tvg_id")
  public String getTvgId() {
    return tvgId;
  }

  public void setTvgId(String tvgId) {
    this.tvgId = tvgId;
  }

  public EpgProgramme startsAt(OffsetDateTime startsAt) {
    this.startsAt = startsAt;
    return this;
  }

  /**
   * Get startsAt
   * @return startsAt
   */
  @NotNull @Valid 
  @JsonProperty("starts_at")
  public OffsetDateTime getStartsAt() {
    return startsAt;
  }

  public void setStartsAt(OffsetDateTime startsAt) {
    this.startsAt = startsAt;
  }

  public EpgProgramme endsAt(OffsetDateTime endsAt) {
    this.endsAt = endsAt;
    return this;
  }

  /**
   * Get endsAt
   * @return endsAt
   */
  @NotNull @Valid 
  @JsonProperty("ends_at")
  public OffsetDateTime getEndsAt() {
    return endsAt;
  }

  public void setEndsAt(OffsetDateTime endsAt) {
    this.endsAt = endsAt;
  }

  public EpgProgramme title(String title) {
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

  public EpgProgramme description(@Nullable String description) {
    this.description = description;
    return this;
  }

  /**
   * Get description
   * @return description
   */
  
  @JsonProperty("description")
  public @Nullable String getDescription() {
    return description;
  }

  public void setDescription(@Nullable String description) {
    this.description = description;
  }

  public EpgProgramme category(@Nullable String category) {
    this.category = category;
    return this;
  }

  /**
   * Genre as advertised by the guide. Free-form, not an enum.
   * @return category
   */
  
  @JsonProperty("category")
  public @Nullable String getCategory() {
    return category;
  }

  public void setCategory(@Nullable String category) {
    this.category = category;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EpgProgramme epgProgramme = (EpgProgramme) o;
    return Objects.equals(this.id, epgProgramme.id) &&
        Objects.equals(this.sourceId, epgProgramme.sourceId) &&
        Objects.equals(this.tvgId, epgProgramme.tvgId) &&
        Objects.equals(this.startsAt, epgProgramme.startsAt) &&
        Objects.equals(this.endsAt, epgProgramme.endsAt) &&
        Objects.equals(this.title, epgProgramme.title) &&
        Objects.equals(this.description, epgProgramme.description) &&
        Objects.equals(this.category, epgProgramme.category);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, sourceId, tvgId, startsAt, endsAt, title, description, category);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EpgProgramme {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    sourceId: ").append(toIndentedString(sourceId)).append("\n");
    sb.append("    tvgId: ").append(toIndentedString(tvgId)).append("\n");
    sb.append("    startsAt: ").append(toIndentedString(startsAt)).append("\n");
    sb.append("    endsAt: ").append(toIndentedString(endsAt)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    category: ").append(toIndentedString(category)).append("\n");
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

