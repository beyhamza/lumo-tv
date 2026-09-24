package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.EpgMappingStatus;
import tv.lumo.api.generated.model.EpgProgramme;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * One row of the grid: one requested channel and its programmes over the window. Identified by the catalogue&#39;s UUID and never by a name — a name is what the next ingestion changes. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class EpgChannelProgrammes {

  private UUID channelId;

  private EpgMappingStatus mappingStatus;

  @Valid
  private List<@Valid EpgProgramme> programmes = new ArrayList<>();

  public EpgChannelProgrammes() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public EpgChannelProgrammes(UUID channelId, EpgMappingStatus mappingStatus, List<@Valid EpgProgramme> programmes) {
    this.channelId = channelId;
    this.mappingStatus = mappingStatus;
    this.programmes = programmes;
  }

  public EpgChannelProgrammes channelId(UUID channelId) {
    this.channelId = channelId;
    return this;
  }

  /**
   * The identifier as it was requested.
   * @return channelId
   */
  @NotNull @Valid 
  @JsonProperty("channel_id")
  public UUID getChannelId() {
    return channelId;
  }

  public void setChannelId(UUID channelId) {
    this.channelId = channelId;
  }

  public EpgChannelProgrammes mappingStatus(EpgMappingStatus mappingStatus) {
    this.mappingStatus = mappingStatus;
    return this;
  }

  /**
   * Get mappingStatus
   * @return mappingStatus
   */
  @NotNull @Valid 
  @JsonProperty("mapping_status")
  public EpgMappingStatus getMappingStatus() {
    return mappingStatus;
  }

  public void setMappingStatus(EpgMappingStatus mappingStatus) {
    this.mappingStatus = mappingStatus;
  }

  public EpgChannelProgrammes programmes(List<@Valid EpgProgramme> programmes) {
    this.programmes = programmes;
    return this;
  }

  public EpgChannelProgrammes addProgrammesItem(EpgProgramme programmesItem) {
    if (this.programmes == null) {
      this.programmes = new ArrayList<>();
    }
    this.programmes.add(programmesItem);
    return this;
  }

  /**
   * Every stored programme overlapping the window, with its full times, ordered by `starts_at` then `id`. Empty under `NO_TVG_ID`, and empty under `MAPPED` when the guide has nothing for this channel over this window. No stream URL is added here or anywhere near here. 
   * @return programmes
   */
  @NotNull @Valid 
  @JsonProperty("programmes")
  public List<@Valid EpgProgramme> getProgrammes() {
    return programmes;
  }

  public void setProgrammes(List<@Valid EpgProgramme> programmes) {
    this.programmes = programmes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EpgChannelProgrammes epgChannelProgrammes = (EpgChannelProgrammes) o;
    return Objects.equals(this.channelId, epgChannelProgrammes.channelId) &&
        Objects.equals(this.mappingStatus, epgChannelProgrammes.mappingStatus) &&
        Objects.equals(this.programmes, epgChannelProgrammes.programmes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(channelId, mappingStatus, programmes);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EpgChannelProgrammes {\n");
    sb.append("    channelId: ").append(toIndentedString(channelId)).append("\n");
    sb.append("    mappingStatus: ").append(toIndentedString(mappingStatus)).append("\n");
    sb.append("    programmes: ").append(toIndentedString(programmes)).append("\n");
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

