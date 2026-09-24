package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.EpgChannelProgrammes;
import tv.lumo.api.generated.model.EpgImportStatus;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * The answer to &#x60;GET /sources/{id}/epg&#x60;: the whole batch, or nothing. Bounded by the two ceilings the operation describes and never truncated to fit under them. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class EpgGrid {

  private UUID sourceId;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime from;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime to;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime generatedAt;

  private EpgImportStatus epg;

  @Valid
  private List<@Valid EpgChannelProgrammes> channels = new ArrayList<>();

  public EpgGrid() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public EpgGrid(UUID sourceId, OffsetDateTime from, OffsetDateTime to, OffsetDateTime generatedAt, EpgImportStatus epg, List<@Valid EpgChannelProgrammes> channels) {
    this.sourceId = sourceId;
    this.from = from;
    this.to = to;
    this.generatedAt = generatedAt;
    this.epg = epg;
    this.channels = channels;
  }

  public EpgGrid sourceId(UUID sourceId) {
    this.sourceId = sourceId;
    return this;
  }

  /**
   * The source that was verified to be the caller's.
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

  public EpgGrid from(OffsetDateTime from) {
    this.from = from;
    return this;
  }

  /**
   * The effective inclusive lower bound, defaults applied.
   * @return from
   */
  @NotNull @Valid 
  @JsonProperty("from")
  public OffsetDateTime getFrom() {
    return from;
  }

  public void setFrom(OffsetDateTime from) {
    this.from = from;
  }

  public EpgGrid to(OffsetDateTime to) {
    this.to = to;
    return this;
  }

  /**
   * The effective exclusive upper bound, defaults applied.
   * @return to
   */
  @NotNull @Valid 
  @JsonProperty("to")
  public OffsetDateTime getTo() {
    return to;
  }

  public void setTo(OffsetDateTime to) {
    this.to = to;
  }

  public EpgGrid generatedAt(OffsetDateTime generatedAt) {
    this.generatedAt = generatedAt;
    return this;
  }

  /**
   * The server's clock when this answer was produced. It is the reference for computing the age of the guide against `epg.last_successful_import_at`; it is **not** the age of the guide itself, and a client must not present it as one. 
   * @return generatedAt
   */
  @NotNull @Valid 
  @JsonProperty("generated_at")
  public OffsetDateTime getGeneratedAt() {
    return generatedAt;
  }

  public void setGeneratedAt(OffsetDateTime generatedAt) {
    this.generatedAt = generatedAt;
  }

  public EpgGrid epg(EpgImportStatus epg) {
    this.epg = epg;
    return this;
  }

  /**
   * Get epg
   * @return epg
   */
  @NotNull @Valid 
  @JsonProperty("epg")
  public EpgImportStatus getEpg() {
    return epg;
  }

  public void setEpg(EpgImportStatus epg) {
    this.epg = epg;
  }

  public EpgGrid channels(List<@Valid EpgChannelProgrammes> channels) {
    this.channels = channels;
    return this;
  }

  public EpgGrid addChannelsItem(EpgChannelProgrammes channelsItem) {
    if (this.channels == null) {
      this.channels = new ArrayList<>();
    }
    this.channels.add(channelsItem);
    return this;
  }

  /**
   * One entry per identifier in `channelIds`, in the order they were sent, empty lists included. 
   * @return channels
   */
  @NotNull @Valid 
  @JsonProperty("channels")
  public List<@Valid EpgChannelProgrammes> getChannels() {
    return channels;
  }

  public void setChannels(List<@Valid EpgChannelProgrammes> channels) {
    this.channels = channels;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EpgGrid epgGrid = (EpgGrid) o;
    return Objects.equals(this.sourceId, epgGrid.sourceId) &&
        Objects.equals(this.from, epgGrid.from) &&
        Objects.equals(this.to, epgGrid.to) &&
        Objects.equals(this.generatedAt, epgGrid.generatedAt) &&
        Objects.equals(this.epg, epgGrid.epg) &&
        Objects.equals(this.channels, epgGrid.channels);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceId, from, to, generatedAt, epg, channels);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EpgGrid {\n");
    sb.append("    sourceId: ").append(toIndentedString(sourceId)).append("\n");
    sb.append("    from: ").append(toIndentedString(from)).append("\n");
    sb.append("    to: ").append(toIndentedString(to)).append("\n");
    sb.append("    generatedAt: ").append(toIndentedString(generatedAt)).append("\n");
    sb.append("    epg: ").append(toIndentedString(epg)).append("\n");
    sb.append("    channels: ").append(toIndentedString(channels)).append("\n");
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

