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
 * A film.  **Not a &#x60;Channel&#x60; with extra columns.** A channel is played; a film is *chosen*, and nobody chooses without a poster, a year and a running time. Putting those on &#x60;Channel&#x60; would mean six null columns on the fifteen thousand rows of an ordinary channel list.  **&#x60;stream_url&#x60; is absent**, for the reason it is absent from &#x60;Channel&#x60;: it is credential-bearing and comes from &#x60;GET /vod/{id}/playback&#x60;, one film at a time, after an ownership check.  **&#x60;container_extension&#x60; is absent too, and that one is not about secrecy.** It is a fragment the server uses to build an Xtream playback URL, it is null for every M3U film (&#x60;adr/0009&#x60;), and no client has anything to do with it. A field that is meaningless to every consumer is not a field the contract should carry. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class VodItem {

  private UUID id;

  private UUID sourceId;

  private @Nullable UUID categoryId = null;

  private @Nullable String externalId = null;

  private String name;

  private @Nullable String posterUrl = null;

  private @Nullable Integer year = null;

  private @Nullable Integer durationSeconds = null;

  private @Nullable String rating = null;

  private @Nullable String plot = null;

  private Integer position;

  private Boolean isAdult;

  public VodItem() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public VodItem(UUID id, UUID sourceId, String name, Integer position, Boolean isAdult) {
    this.id = id;
    this.sourceId = sourceId;
    this.name = name;
    this.position = position;
    this.isAdult = isAdult;
  }

  public VodItem id(UUID id) {
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

  public VodItem sourceId(UUID sourceId) {
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

  public VodItem categoryId(@Nullable UUID categoryId) {
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

  public VodItem externalId(@Nullable String externalId) {
    this.externalId = externalId;
    return this;
  }

  /**
   * Identifier used by the origin panel or playlist.
   * @return externalId
   */
  
  @JsonProperty("external_id")
  public @Nullable String getExternalId() {
    return externalId;
  }

  public void setExternalId(@Nullable String externalId) {
    this.externalId = externalId;
  }

  public VodItem name(String name) {
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

  public VodItem posterUrl(@Nullable String posterUrl) {
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

  public VodItem year(@Nullable Integer year) {
    this.year = year;
    return this;
  }

  /**
   * Release year, when the source states one. Many do not.
   * @return year
   */
  
  @JsonProperty("year")
  public @Nullable Integer getYear() {
    return year;
  }

  public void setYear(@Nullable Integer year) {
    this.year = year;
  }

  public VodItem durationSeconds(@Nullable Integer durationSeconds) {
    this.durationSeconds = durationSeconds;
    return this;
  }

  /**
   * Running time. Null far more often than not — and a client that needs it to decide whether something was watched to the end has to cope without it (see `PlaybackProgress.duration_ms`). 
   * @return durationSeconds
   */
  
  @JsonProperty("duration_seconds")
  public @Nullable Integer getDurationSeconds() {
    return durationSeconds;
  }

  public void setDurationSeconds(@Nullable Integer durationSeconds) {
    this.durationSeconds = durationSeconds;
  }

  public VodItem rating(@Nullable String rating) {
    this.rating = rating;
    return this;
  }

  /**
   * Whatever the source calls a rating, echoed verbatim and never reinterpreted — `7.4`, `PG-13` and `★★★★` all occur. A number here would mean this layer deciding what the provider meant, which is the decision already refused for `Channel.quality`. 
   * @return rating
   */
  
  @JsonProperty("rating")
  public @Nullable String getRating() {
    return rating;
  }

  public void setRating(@Nullable String rating) {
    this.rating = rating;
  }

  public VodItem plot(@Nullable String plot) {
    this.plot = plot;
    return this;
  }

  /**
   * Synopsis, and **absent from the listing on purpose**: it is fetched when somebody opens a film rather than when they scroll past a thousand.  The reason is not response size, it is the user's own server: on an Xtream panel the synopsis comes from `get_vod_info`, which is **one HTTP call per film**. Loading it for a catalogue of thirty thousand at every synchronisation is not slow — it is the kind of thing that gets our address banned by somebody's provider.  So it is null in a page, and populated on the single-film read that fills it in. 
   * @return plot
   */
  
  @JsonProperty("plot")
  public @Nullable String getPlot() {
    return plot;
  }

  public void setPlot(@Nullable String plot) {
    this.plot = plot;
  }

  public VodItem position(Integer position) {
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

  public VodItem isAdult(Boolean isAdult) {
    this.isAdult = isAdult;
    return this;
  }

  /**
   * As the source flags it. Exposed and **not acted on**: filtering it is a parental control, parental control needs profiles, and profiles are v2 (`AGENTS.md` §6). 
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
    VodItem vodItem = (VodItem) o;
    return Objects.equals(this.id, vodItem.id) &&
        Objects.equals(this.sourceId, vodItem.sourceId) &&
        Objects.equals(this.categoryId, vodItem.categoryId) &&
        Objects.equals(this.externalId, vodItem.externalId) &&
        Objects.equals(this.name, vodItem.name) &&
        Objects.equals(this.posterUrl, vodItem.posterUrl) &&
        Objects.equals(this.year, vodItem.year) &&
        Objects.equals(this.durationSeconds, vodItem.durationSeconds) &&
        Objects.equals(this.rating, vodItem.rating) &&
        Objects.equals(this.plot, vodItem.plot) &&
        Objects.equals(this.position, vodItem.position) &&
        Objects.equals(this.isAdult, vodItem.isAdult);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, sourceId, categoryId, externalId, name, posterUrl, year, durationSeconds, rating, plot, position, isAdult);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class VodItem {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    sourceId: ").append(toIndentedString(sourceId)).append("\n");
    sb.append("    categoryId: ").append(toIndentedString(categoryId)).append("\n");
    sb.append("    externalId: ").append(toIndentedString(externalId)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    posterUrl: ").append(toIndentedString(posterUrl)).append("\n");
    sb.append("    year: ").append(toIndentedString(year)).append("\n");
    sb.append("    durationSeconds: ").append(toIndentedString(durationSeconds)).append("\n");
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

