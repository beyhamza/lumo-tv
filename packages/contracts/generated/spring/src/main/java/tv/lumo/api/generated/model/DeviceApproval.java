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
 * The television an approval just bound to the account, as **it declared itself** when it requested the code.  Deliberately not a &#x60;Device&#x60;: no &#x60;device&#x60; row exists at this point, and inventing one for a set that may never poll would put a phantom installation in the user&#39;s list and against their quota. The fields are the ones &#x60;DeviceCodeRequest&#x60; carried, echoed back.  They are client-supplied and unverified, so they are good enough to name a set on a confirmation screen and never good enough to be treated as identity. Render &#x60;name&#x60; as a label; do not key anything on it. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class DeviceApproval {

  private Platform platform;

  private @Nullable String name = null;

  private @Nullable String model = null;

  private @Nullable String appVersion = null;

  public DeviceApproval() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public DeviceApproval(Platform platform) {
    this.platform = platform;
  }

  public DeviceApproval platform(Platform platform) {
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

  public DeviceApproval name(@Nullable String name) {
    this.name = name;
    return this;
  }

  /**
   * User-facing device name, as the television reported it.
   * @return name
   */
  
  @JsonProperty("name")
  public @Nullable String getName() {
    return name;
  }

  public void setName(@Nullable String name) {
    this.name = name;
  }

  public DeviceApproval model(@Nullable String model) {
    this.model = model;
    return this;
  }

  /**
   * Get model
   * @return model
   */
  
  @JsonProperty("model")
  public @Nullable String getModel() {
    return model;
  }

  public void setModel(@Nullable String model) {
    this.model = model;
  }

  public DeviceApproval appVersion(@Nullable String appVersion) {
    this.appVersion = appVersion;
    return this;
  }

  /**
   * Get appVersion
   * @return appVersion
   */
  
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
    DeviceApproval deviceApproval = (DeviceApproval) o;
    return Objects.equals(this.platform, deviceApproval.platform) &&
        Objects.equals(this.name, deviceApproval.name) &&
        Objects.equals(this.model, deviceApproval.model) &&
        Objects.equals(this.appVersion, deviceApproval.appVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(platform, name, model, appVersion);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeviceApproval {\n");
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

