package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.Platform;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Sent by the television to start an RFC 8628 authorization.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class DeviceCodeRequest {

  private Platform platform;

  private @Nullable String name = null;

  private @Nullable String model = null;

  private @Nullable String appVersion = null;

  public DeviceCodeRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public DeviceCodeRequest(Platform platform) {
    this.platform = platform;
  }

  public DeviceCodeRequest platform(Platform platform) {
    this.platform = platform;
    return this;
  }

  /**
   * Get platform
   * @return platform
   */
  @NotNull @Valid 
  @JsonProperty("platform")
  public Platform getPlatform() {
    return platform;
  }

  public void setPlatform(Platform platform) {
    this.platform = platform;
  }

  public DeviceCodeRequest name(@Nullable String name) {
    this.name = name;
    return this;
  }

  /**
   * Get name
   * @return name
   */
  @Size(max = 100) 
  @JsonProperty("name")
  public @Nullable String getName() {
    return name;
  }

  public void setName(@Nullable String name) {
    this.name = name;
  }

  public DeviceCodeRequest model(@Nullable String model) {
    this.model = model;
    return this;
  }

  /**
   * Get model
   * @return model
   */
  @Size(max = 100) 
  @JsonProperty("model")
  public @Nullable String getModel() {
    return model;
  }

  public void setModel(@Nullable String model) {
    this.model = model;
  }

  public DeviceCodeRequest appVersion(@Nullable String appVersion) {
    this.appVersion = appVersion;
    return this;
  }

  /**
   * Get appVersion
   * @return appVersion
   */
  @Size(max = 40) 
  @JsonProperty("app_version")
  public @Nullable String getAppVersion() {
    return appVersion;
  }

  public void setAppVersion(@Nullable String appVersion) {
    this.appVersion = appVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DeviceCodeRequest deviceCodeRequest = (DeviceCodeRequest) o;
    return Objects.equals(this.platform, deviceCodeRequest.platform) &&
        Objects.equals(this.name, deviceCodeRequest.name) &&
        Objects.equals(this.model, deviceCodeRequest.model) &&
        Objects.equals(this.appVersion, deviceCodeRequest.appVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(platform, name, model, appVersion);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeviceCodeRequest {\n");
    sb.append("    platform: ").append(toIndentedString(platform)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    model: ").append(toIndentedString(model)).append("\n");
    sb.append("    appVersion: ").append(toIndentedString(appVersion)).append("\n");
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

