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
 * &#x60;source_id&#x60; is not accepted: it is derived from the channel, exactly as on &#x60;AddFavoriteRequest&#x60;. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class RecordRecentChannelRequest {

  private UUID channelId;

  public RecordRecentChannelRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public RecordRecentChannelRequest(UUID channelId) {
    this.channelId = channelId;
  }

  public RecordRecentChannelRequest channelId(UUID channelId) {
    this.channelId = channelId;
    return this;
  }

  /**
   * Get channelId
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RecordRecentChannelRequest recordRecentChannelRequest = (RecordRecentChannelRequest) o;
    return Objects.equals(this.channelId, recordRecentChannelRequest.channelId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(channelId);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class RecordRecentChannelRequest {\n");
    sb.append("    channelId: ").append(toIndentedString(channelId)).append("\n");
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

