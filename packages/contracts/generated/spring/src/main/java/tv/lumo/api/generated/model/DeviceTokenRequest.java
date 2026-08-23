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
 * DeviceTokenRequest
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class DeviceTokenRequest {

  private String deviceCode;

  public DeviceTokenRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public DeviceTokenRequest(String deviceCode) {
    this.deviceCode = deviceCode;
  }

  public DeviceTokenRequest deviceCode(String deviceCode) {
    this.deviceCode = deviceCode;
    return this;
  }

  /**
   * The secret returned by `POST /auth/device/code`. Masked in generated `toString()`.
   * @return deviceCode
   */
  @NotNull @Size(max = 512) 
  @JsonProperty("device_code")
  public String getDeviceCode() {
    return deviceCode;
  }

  public void setDeviceCode(String deviceCode) {
    this.deviceCode = deviceCode;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DeviceTokenRequest deviceTokenRequest = (DeviceTokenRequest) o;
    return Objects.equals(this.deviceCode, deviceTokenRequest.deviceCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(deviceCode);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeviceTokenRequest {\n");
    sb.append("    deviceCode: ").append("*").append("\n");
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

