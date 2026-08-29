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
 * A series, as a listing carries one.  **A &#x60;VodItem&#x60; minus one field and plus one.** No &#x60;container_extension&#x60;, because a series is not played — its episodes are. And an &#x60;episode_run_time&#x60;, which is indicative rather than authoritative: it is what the panel says a typical episode lasts, not the length of any particular one.  Everything else is a film&#39;s, deliberately: &#x60;poster_url&#x60; nullable, &#x60;plot&#x60; absent from listings and filled by &#x60;GET /series/{id}&#x60;, &#x60;rating&#x60; echoed verbatim. A client that wrote a film card can draw this one.  **No season and no episode here.** They cost a call to the user&#39;s own server, per series — see &#x60;GET /sources/{id}/series&#x60;. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class Series {

  private UUID id;

  private UUID sourceId;

  private @Nullable UUID categoryId = null;

  private @Nullable String externalId = null;

  private String name;

  private @Nullable String posterUrl = null;

  private @Nullable Integer year = null;

  private @Nullable Integer episodeRunTime = null;

  private @Nullable String rating = null;

  private @Nullable String plot = null;

  private Integer position;

  private Boolean isAdult;

  public Series() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public Series(UUID id, UUID sourceId, String name, Integer position, Boolean isAdult) {
    this.id = id;
    this.sourceId = sourceId;
    this.name = name;
    this.position = position;
    this.isAdult = isAdult;
  }

  public Series id(UUID id) {
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

  public Series sourceId(UUID sourceId) {
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

  public Series categoryId(@Nullable UUID categoryId) {
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

  public Series externalId(@Nullable String externalId) {
    this.externalId = externalId;
    return this;
  }

  /**
   * Identifier used by the origin panel.
   * @return externalId
   */
  
  @JsonProperty("external_id")
  public @Nullable String getExternalId() {
    return externalId;
  }

  public void setExternalId(@Nullable String externalId) {
    this.externalId = externalId;
  }

  public Series name(String name) {
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

  public Series posterUrl(@Nullable String posterUrl) {
    this.posterUrl = posterUrl;
    return this;
  }

  /**
   * Artwork advertised by the user's own source. Lumo ships no bundled poster and no fallback of its own; null means a client renders the title, never a picture of ours standing in for one of theirs. 
   * @return posterUrl
   */
  
  @JsonProperty("poster_url")
  public @Nullable String getPosterUrl() {
    return posterUrl;
  }

  public void setPosterUrl(@Nullable String posterUrl) {
    this.posterUrl = posterUrl;
  }

  public Series year(@Nullable Integer year) {
    this.year = year;
    return this;
  }

  /**
   * First-broadcast year, when the source states one.
   * @return year
   */
  
  @JsonProperty("year")
  public @Nullable Integer getYear() {
    return year;
  }

  public void setYear(@Nullable Integer year) {
    this.year = year;
  }

  public Series episodeRunTime(@Nullable Integer episodeRunTime) {
    this.episodeRunTime = episodeRunTime;
    return this;
  }

  /**
   * Typical episode length in minutes, as the panel states it.  **Indicative, and never used as a duration.** A saved position needs the length of the episode being watched, which is `Episode.duration_seconds`; using this in its place would compute \"finished\" against a number that belongs to no episode in particular. 
   * @return episodeRunTime
   */
  
  @JsonProperty("episode_run_time")
  public @Nullable Integer getEpisodeRunTime() {
    return episodeRunTime;
  }

  public void setEpisodeRunTime(@Nullable Integer episodeRunTime) {
    this.episodeRunTime = episodeRunTime;
  }

  public Series rating(@Nullable String rating) {
    this.rating = rating;
    return this;
  }

  /**
   * Whatever the source calls a rating, echoed verbatim and never reinterpreted — the same ruling as `VodItem.rating`. 
   * @return rating
   */
  
  @JsonProperty("rating")
  public @Nullable String getRating() {
    return rating;
  }

  public void setRating(@Nullable String rating) {
    this.rating = rating;
  }

  public Series plot(@Nullable String plot) {
    this.plot = plot;
    return this;
  }

  /**
   * Synopsis, and **absent from the listing on purpose**, exactly as on `VodItem`: it arrives with the tree, from the single call that `GET /series/{id}` makes. 
   * @return plot
   */
  
  @JsonProperty("plot")
  public @Nullable String getPlot() {
    return plot;
  }

  public void setPlot(@Nullable String plot) {
    this.plot = plot;
  }

  public Series position(Integer position) {
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

  public Series isAdult(Boolean isAdult) {
    this.isAdult = isAdult;
    return this;
  }

  /**
   * As the source flags it. Exposed and **not acted on**: filtering it is a parental control, parental control needs profiles, and profiles are v2. 
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
    Series series = (Series) o;
    return Objects.equals(this.id, series.id) &&
        Objects.equals(this.sourceId, series.sourceId) &&
        Objects.equals(this.categoryId, series.categoryId) &&
        Objects.equals(this.externalId, series.externalId) &&
        Objects.equals(this.name, series.name) &&
        Objects.equals(this.posterUrl, series.posterUrl) &&
        Objects.equals(this.year, series.year) &&
        Objects.equals(this.episodeRunTime, series.episodeRunTime) &&
        Objects.equals(this.rating, series.rating) &&
        Objects.equals(this.plot, series.plot) &&
        Objects.equals(this.position, series.position) &&
        Objects.equals(this.isAdult, series.isAdult);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, sourceId, categoryId, externalId, name, posterUrl, year, episodeRunTime, rating, plot, position, isAdult);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Series {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    sourceId: ").append(toIndentedString(sourceId)).append("\n");
    sb.append("    categoryId: ").append(toIndentedString(categoryId)).append("\n");
    sb.append("    externalId: ").append(toIndentedString(externalId)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    posterUrl: ").append(toIndentedString(posterUrl)).append("\n");
    sb.append("    year: ").append(toIndentedString(year)).append("\n");
    sb.append("    episodeRunTime: ").append(toIndentedString(episodeRunTime)).append("\n");
    sb.append("    rating: ").append(toIndentedString(rating)).append("\n");
    sb.append("    plot: ").append(toIndentedString(plot)).append("\n");
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

