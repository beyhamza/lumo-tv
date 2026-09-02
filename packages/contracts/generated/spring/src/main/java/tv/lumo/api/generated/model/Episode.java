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
 * One episode. The thing that is actually played, and the thing a saved position points at.  **&#x60;container_extension&#x60; is absent**, for the reason it is absent from &#x60;VodItem&#x60;: it is a fragment the server uses to build a playback URL, and no client has anything to do with it.  **&#x60;series_id&#x60; is present**, and it is the field that makes a resume rail possible: &#x60;GET /me/progress&#x60; returns episode identifiers, and something has to group them back into series before anything can be drawn. See &#x60;GET /sources/{id}/episodes&#x60;. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class Episode {

  private UUID id;

  private UUID seriesId;

  private UUID sourceId;

  private @Nullable String externalId = null;

  private Integer seasonNumber;

  private Integer episodeNumber;

  private @Nullable String name = null;

  private @Nullable Long durationSeconds = null;

  private @Nullable String plot = null;

  private @Nullable String audioCodec = null;

  private @Nullable Integer audioChannels = null;

  public Episode() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public Episode(UUID id, UUID seriesId, UUID sourceId, Integer seasonNumber, Integer episodeNumber) {
    this.id = id;
    this.seriesId = seriesId;
    this.sourceId = sourceId;
    this.seasonNumber = seasonNumber;
    this.episodeNumber = episodeNumber;
  }

  public Episode id(UUID id) {
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

  public Episode seriesId(UUID seriesId) {
    this.seriesId = seriesId;
    return this;
  }

  /**
   * The series this episode belongs to. See the schema note.
   * @return seriesId
   */
  @NotNull @Valid 
  @JsonProperty("series_id")
  public UUID getSeriesId() {
    return seriesId;
  }

  public void setSeriesId(UUID seriesId) {
    this.seriesId = seriesId;
  }

  public Episode sourceId(UUID sourceId) {
    this.sourceId = sourceId;
    return this;
  }

  /**
   * Carried for the same reason `PlaybackProgress` carries one: a client resolving a rail has to know which source to ask, and deriving it from the series would mean a lookup it does not have. 
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

  public Episode externalId(@Nullable String externalId) {
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

  public Episode seasonNumber(Integer seasonNumber) {
    this.seasonNumber = seasonNumber;
    return this;
  }

  /**
   * Get seasonNumber
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

  public Episode episodeNumber(Integer episodeNumber) {
    this.episodeNumber = episodeNumber;
    return this;
  }

  /**
   * Get episodeNumber
   * minimum: 0
   * @return episodeNumber
   */
  @NotNull @Min(0) 
  @JsonProperty("episode_number")
  public Integer getEpisodeNumber() {
    return episodeNumber;
  }

  public void setEpisodeNumber(Integer episodeNumber) {
    this.episodeNumber = episodeNumber;
  }

  public Episode name(@Nullable String name) {
    this.name = name;
    return this;
  }

  /**
   * Episode title, when the panel has one. **Null far more often than for a film**, and a client shows \"Episode 4\" rather than an empty line — the number is always there, the title is not. 
   * @return name
   */
  
  @JsonProperty("name")
  public @Nullable String getName() {
    return name;
  }

  public void setName(@Nullable String name) {
    this.name = name;
  }

  public Episode durationSeconds(@Nullable Long durationSeconds) {
    this.durationSeconds = durationSeconds;
    return this;
  }

  /**
   * Length of *this* episode, when the source states one. This is what a client uses to decide an episode is finished, never `Series.episode_run_time`. 
   * @return durationSeconds
   */
  
  @JsonProperty("duration_seconds")
  public @Nullable Long getDurationSeconds() {
    return durationSeconds;
  }

  public void setDurationSeconds(@Nullable Long durationSeconds) {
    this.durationSeconds = durationSeconds;
  }

  public Episode plot(@Nullable String plot) {
    this.plot = plot;
    return this;
  }

  /**
   * Episode synopsis, when the panel supplies one. It arrives with the tree, so unlike a film's it costs no extra call — one `get_series_info` returns every episode's. 
   * @return plot
   */
  
  @JsonProperty("plot")
  public @Nullable String getPlot() {
    return plot;
  }

  public void setPlot(@Nullable String plot) {
    this.plot = plot;
  }

  public Episode audioCodec(@Nullable String audioCodec) {
    this.audioCodec = audioCodec;
    return this;
  }

  /**
   * The audio codec of this episode, **echoed verbatim** — `ac3`, `eac3`, `aac`, `dts`. Whatever the panel calls it, never reinterpreted, for the reason `Channel.quality` is not: deciding what a provider meant is a decision this layer does not get to make.  **It is here so a client can warn before playing rather than after.** A browser decodes picture and sound separately, and no browser ships a Dolby Digital decoder — so an episode in `ac3` plays perfectly and silently, behind a mute button that does nothing. On a real catalogue that is roughly a third of the episodes. The web client says so on the episode before it is opened; the applications ignore this field entirely, because they decode it.  **This is not the server deciding what a client can play.** It is a fact about the file. Which codecs a given player handles is that player's business and changes with the browser, the device and the year — computing it here would freeze one client's limits into the contract.  **Null is normal and means \"not known\"**, not \"no audio\": panels state it inconsistently, and every episode ingested before this field existed has none until the next synchronisation. A client that finds null says nothing. 
   * @return audioCodec
   */
  
  @JsonProperty("audio_codec")
  public @Nullable String getAudioCodec() {
    return audioCodec;
  }

  public void setAudioCodec(@Nullable String audioCodec) {
    this.audioCodec = audioCodec;
  }

  public Episode audioChannels(@Nullable Integer audioChannels) {
    this.audioChannels = audioChannels;
    return this;
  }

  /**
   * Channel count of that track — `2`, `6` — when the panel states one. Carried beside the codec because it is the other half of the same sentence a client may want to write, and it arrives in the same tree for free. 
   * @return audioChannels
   */
  
  @JsonProperty("audio_channels")
  public @Nullable Integer getAudioChannels() {
    return audioChannels;
  }

  public void setAudioChannels(@Nullable Integer audioChannels) {
    this.audioChannels = audioChannels;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Episode episode = (Episode) o;
    return Objects.equals(this.id, episode.id) &&
        Objects.equals(this.seriesId, episode.seriesId) &&
        Objects.equals(this.sourceId, episode.sourceId) &&
        Objects.equals(this.externalId, episode.externalId) &&
        Objects.equals(this.seasonNumber, episode.seasonNumber) &&
        Objects.equals(this.episodeNumber, episode.episodeNumber) &&
        Objects.equals(this.name, episode.name) &&
        Objects.equals(this.durationSeconds, episode.durationSeconds) &&
        Objects.equals(this.plot, episode.plot) &&
        Objects.equals(this.audioCodec, episode.audioCodec) &&
        Objects.equals(this.audioChannels, episode.audioChannels);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, seriesId, sourceId, externalId, seasonNumber, episodeNumber, name, durationSeconds, plot, audioCodec, audioChannels);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Episode {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    seriesId: ").append(toIndentedString(seriesId)).append("\n");
    sb.append("    sourceId: ").append(toIndentedString(sourceId)).append("\n");
    sb.append("    externalId: ").append(toIndentedString(externalId)).append("\n");
    sb.append("    seasonNumber: ").append(toIndentedString(seasonNumber)).append("\n");
    sb.append("    episodeNumber: ").append(toIndentedString(episodeNumber)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    durationSeconds: ").append(toIndentedString(durationSeconds)).append("\n");
    sb.append("    plot: ").append(toIndentedString(plot)).append("\n");
    sb.append("    audioCodec: ").append(toIndentedString(audioCodec)).append("\n");
    sb.append("    audioChannels: ").append(toIndentedString(audioChannels)).append("\n");
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

