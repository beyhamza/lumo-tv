package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.Episode;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * A season, which is an intercalary level and not an object anybody opens for itself.  Hence the four fields and no more: a number, a count, artwork when the panel has some, and the episodes. There is no &#x60;GET /seasons/{id}&#x60; and there should not be — a season is reached through its series and has no life of its own. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class Season {

  private Integer seasonNumber;

  private @Nullable Integer episodeCount = null;

  private @Nullable String posterUrl = null;

  @Valid
  private List<@Valid Episode> episodes = new ArrayList<>();

  public Season() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public Season(Integer seasonNumber, List<@Valid Episode> episodes) {
    this.seasonNumber = seasonNumber;
    this.episodes = episodes;
  }

  public Season seasonNumber(Integer seasonNumber) {
    this.seasonNumber = seasonNumber;
    return this;
  }

  /**
   * As the panel numbers it. **Zero occurs** and means specials on many panels; it is passed through rather than renamed, because deciding it means \"specials\" would be this layer interpreting a provider's convention. 
   * minimum: 0
   * @return seasonNumber
   */
  @NotNull @Min(0) 
  @JsonProperty("season_number")
  public Integer getSeasonNumber() {
    return seasonNumber;
  }

  public void setSeasonNumber(Integer seasonNumber) {
    this.seasonNumber = seasonNumber;
  }

  public Season episodeCount(@Nullable Integer episodeCount) {
    this.episodeCount = episodeCount;
    return this;
  }

  /**
   * How many episodes the panel claims this season has.  **It can disagree with `episodes`, and the list wins.** A panel that announces twenty-four and returns twenty-two has twenty-two episodes somebody can watch. The claim is carried because it is occasionally the only hint that a season is incomplete; it is never what a client counts. 
   * @return episodeCount
   */
  
  @JsonProperty("episode_count")
  public @Nullable Integer getEpisodeCount() {
    return episodeCount;
  }

  public void setEpisodeCount(@Nullable Integer episodeCount) {
    this.episodeCount = episodeCount;
  }

  public Season posterUrl(@Nullable String posterUrl) {
    this.posterUrl = posterUrl;
    return this;
  }

  /**
   * Season artwork when the panel has some, which is uncommon. Null falls back to the series poster — never to a picture of ours. 
   * @return posterUrl
   */
  
  @JsonProperty("poster_url")
  public @Nullable String getPosterUrl() {
    return posterUrl;
  }

  public void setPosterUrl(@Nullable String posterUrl) {
    this.posterUrl = posterUrl;
  }

  public Season episodes(List<@Valid Episode> episodes) {
    this.episodes = episodes;
    return this;
  }

  public Season addEpisodesItem(Episode episodesItem) {
    if (this.episodes == null) {
      this.episodes = new ArrayList<>();
    }
    this.episodes.add(episodesItem);
    return this;
  }

  /**
   * Ordered by `episode_number`.
   * @return episodes
   */
  @NotNull @Valid 
  @JsonProperty("episodes")
  public List<@Valid Episode> getEpisodes() {
    return episodes;
  }

  public void setEpisodes(List<@Valid Episode> episodes) {
    this.episodes = episodes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Season season = (Season) o;
    return Objects.equals(this.seasonNumber, season.seasonNumber) &&
        Objects.equals(this.episodeCount, season.episodeCount) &&
        Objects.equals(this.posterUrl, season.posterUrl) &&
        Objects.equals(this.episodes, season.episodes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(seasonNumber, episodeCount, posterUrl, episodes);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Season {\n");
    sb.append("    seasonNumber: ").append(toIndentedString(seasonNumber)).append("\n");
    sb.append("    episodeCount: ").append(toIndentedString(episodeCount)).append("\n");
    sb.append("    posterUrl: ").append(toIndentedString(posterUrl)).append("\n");
    sb.append("    episodes: ").append(toIndentedString(episodes)).append("\n");
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

