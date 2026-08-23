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
 * ApproveDeviceRequest
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class ApproveDeviceRequest {

  private String userCode;

  public ApproveDeviceRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public ApproveDeviceRequest(String userCode) {
    this.userCode = userCode;
  }

  public ApproveDeviceRequest userCode(String userCode) {
    this.userCode = userCode;
    return this;
  }

  /**
   * The code read off the television screen. Accepted case-insensitively; separators and spaces are stripped before matching, which is why the accepted length is wider than the 8 characters actually stored. 
   * @return userCode
   */
  @NotNull @Size(min = 8, max = 12) 
  @JsonProperty("user_code")
  public String getUserCode() {
    return userCode;
  }

  public void setUserCode(String userCode) {
    this.userCode = userCode;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ApproveDeviceRequest approveDeviceRequest = (ApproveDeviceRequest) o;
    return Objects.equals(this.userCode, approveDeviceRequest.userCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userCode);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ApproveDeviceRequest {\n");
    sb.append("    userCode: ").append(toIndentedString(userCode)).append("\n");
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

