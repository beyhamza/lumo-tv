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
 * Omitted properties are left unchanged. &#x60;channel_id&#x60; is not accepted: pointing a favourite at another channel is adding a different favourite, not editing this one. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class UpdateFavoriteRequest {

  private @Nullable UUID groupId;

  private @Nullable Integer position;

  public UpdateFavoriteRequest groupId(@Nullable UUID groupId) {
    this.groupId = groupId;
    return this;
  }

  /**
   * The group to move this favourite into.
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

  public UpdateFavoriteRequest position(@Nullable Integer position) {
    this.position = position;
    return this;
  }

  /**
   * The index within the target group, counted from zero. Sent alone, it reorders within the group the favourite is already in. 
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
    UpdateFavoriteRequest updateFavoriteRequest = (UpdateFavoriteRequest) o;
    return Objects.equals(this.groupId, updateFavoriteRequest.groupId) &&
        Objects.equals(this.position, updateFavoriteRequest.position);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, position);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class UpdateFavoriteRequest {\n");
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

