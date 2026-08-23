package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * CreateFavoriteGroupRequest
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class CreateFavoriteGroupRequest {

  private String name;

  private @Nullable Integer position = null;

  public CreateFavoriteGroupRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public CreateFavoriteGroupRequest(String name) {
    this.name = name;
  }

  public CreateFavoriteGroupRequest name(String name) {
    this.name = name;
    return this;
  }

  /**
   * Get name
   * @return name
   */
  @NotNull @Size(min = 1, max = 100) 
  @JsonProperty("name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public CreateFavoriteGroupRequest position(@Nullable Integer position) {
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
    CreateFavoriteGroupRequest createFavoriteGroupRequest = (CreateFavoriteGroupRequest) o;
    return Objects.equals(this.name, createFavoriteGroupRequest.name) &&
        Objects.equals(this.position, createFavoriteGroupRequest.position);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, position);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CreateFavoriteGroupRequest {\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
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

