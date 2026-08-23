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
 * &#x60;source_id&#x60; is not accepted: it is derived from the channel, so the two cannot disagree. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class AddFavoriteRequest {

  private UUID channelId;

  private @Nullable UUID groupId = null;

  private @Nullable Integer position = null;

  public AddFavoriteRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public AddFavoriteRequest(UUID channelId) {
    this.channelId = channelId;
  }

  public AddFavoriteRequest channelId(UUID channelId) {
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

  public AddFavoriteRequest groupId(@Nullable UUID groupId) {
    this.groupId = groupId;
    return this;
  }

  /**
   * Defaults to the account's default group, created on first use.
   * @return groupId
   */
  @Valid 
  @JsonProperty("group_id")
  public @Nullable UUID getGroupId() {
    return groupId;
  }

  public void setGroupId(@Nullable UUID groupId) {
    this.groupId = groupId;
  }

  public AddFavoriteRequest position(@Nullable Integer position) {
    this.position = position;
    return this;
  }

  /**
   * Appended last when omitted.
   * @return position
   */
  
  @JsonProperty("position")
  public @Nullable Integer getPosition() {
    return position;
  }

  public void setPosition(@Nullable Integer position) {
    this.position = position;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AddFavoriteRequest addFavoriteRequest = (AddFavoriteRequest) o;
    return Objects.equals(this.channelId, addFavoriteRequest.channelId) &&
        Objects.equals(this.groupId, addFavoriteRequest.groupId) &&
        Objects.equals(this.position, addFavoriteRequest.position);
  }

  @Override
  public int hashCode() {
    return Objects.hash(channelId, groupId, position);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AddFavoriteRequest {\n");
    sb.append("    channelId: ").append(toIndentedString(channelId)).append("\n");
    sb.append("    groupId: ").append(toIndentedString(groupId)).append("\n");
    sb.append("    position: ").append(toIndentedString(position)).append("\n");
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

