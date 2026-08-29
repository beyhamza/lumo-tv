package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.Season;
import tv.lumo.api.generated.model.Series;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * One series and its whole tree, as &#x60;GET /series/{id}&#x60; returns it.  A separate schema from &#x60;Series&#x60; rather than &#x60;Series&#x60; with a nullable &#x60;seasons&#x60;: a listing never carries the tree and a detail read always does, so a shared schema would make every client check for a field that is absent by construction in one case and present by construction in the other. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class SeriesDetail {

  private Series series;

  @Valid
  private List<@Valid Season> seasons = new ArrayList<>();

  public SeriesDetail() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public SeriesDetail(Series series, List<@Valid Season> seasons) {
    this.series = series;
    this.seasons = seasons;
  }

  public SeriesDetail series(Series series) {
    this.series = series;
    return this;
  }

  /**
   * Get series
   * @return series
   */
  @NotNull @Valid 
  @JsonProperty("series")
  public Series getSeries() {
    return series;
  }

  public void setSeries(Series series) {
    this.series = series;
  }

  public SeriesDetail seasons(List<@Valid Season> seasons) {
    this.seasons = seasons;
    return this;
  }

  public SeriesDetail addSeasonsItem(Season seasonsItem) {
    if (this.seasons == null) {
      this.seasons = new ArrayList<>();
    }
    this.seasons.add(seasonsItem);
    return this;
  }

  /**
   * Ordered by `season_number`. **May be empty**, and that is not an error: some panels list a series and answer `get_series_info` with nothing. A client shows the series with no episodes rather than a failure — the series exists, its tree does not. 
   * @return seasons
   */
  @NotNull @Valid 
  @JsonProperty("seasons")
  public List<@Valid Season> getSeasons() {
    return seasons;
  }

  public void setSeasons(List<@Valid Season> seasons) {
    this.seasons = seasons;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SeriesDetail seriesDetail = (SeriesDetail) o;
    return Objects.equals(this.series, seriesDetail.series) &&
        Objects.equals(this.seasons, seriesDetail.seasons);
  }

  @Override
  public int hashCode() {
    return Objects.hash(series, seasons);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SeriesDetail {\n");
    sb.append("    series: ").append(toIndentedString(series)).append("\n");
    sb.append("    seasons: ").append(toIndentedString(seasons)).append("\n");
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

