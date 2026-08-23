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
 * Identifies the installation opening the session. A &#x60;device&#x60; row is created or refreshed, and the issued refresh token is bound to it — that binding is what lets &#x60;DELETE /me/devices/{id}&#x60; revoke one device without touching the others. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class DeviceRegistration {

  private Platform platform;

  private @Nullable String name = null;

  private @Nullable String model = null;

  private @Nullable String appVersion = null;

  public DeviceRegistration() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public DeviceRegistration(Platform platform) {
    this.platform = platform;
  }

  public DeviceRegistration platform(Platform platform) {
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

  public DeviceRegistration name(@Nullable String name) {
    this.name = name;
    return this;
  }

  /**
   * User-facing device name.
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

  public DeviceRegistration model(@Nullable String model) {
    this.model = model;
    return this;
  }

  /**
   * Hardware model reported by the OS.
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

  public DeviceRegistration appVersion(@Nullable String appVersion) {
    this.appVersion = appVersion;
    return this;
  }

  /**
   * Version of the installed application.
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
    DeviceRegistration deviceRegistration = (DeviceRegistration) o;
    return Objects.equals(this.platform, deviceRegistration.platform) &&
        Objects.equals(this.name, deviceRegistration.name) &&
        Objects.equals(this.model, deviceRegistration.model) &&
        Objects.equals(this.appVersion, deviceRegistration.appVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(platform, name, model, appVersion);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeviceRegistration {\n");
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

