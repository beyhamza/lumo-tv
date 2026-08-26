package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.Platform;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * One installation linked to the account.  &#x60;is_current&#x60; is computed against the access token presented on the request, which is why it is a property of the response and not something the caller works out for itself. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class Device {

  private UUID id;

  private Platform platform;

  private @Nullable String name = null;

  private @Nullable String model = null;

  private @Nullable String appVersion = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private @Nullable OffsetDateTime lastSeenAt = null;

  private Boolean isCurrent;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime createdAt;

  public Device() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public Device(UUID id, Platform platform, Boolean isCurrent, OffsetDateTime createdAt) {
    this.id = id;
    this.platform = platform;
    this.isCurrent = isCurrent;
    this.createdAt = createdAt;
  }

  public Device id(UUID id) {
    this.id = id;
    return this;
  }

  /**
   * Get id
   * @return id
   */
  @NotNull @Valid 
  @JsonProperty("id")
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Device platform(Platform platform) {
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

  public Device name(@Nullable String name) {
    this.name = name;
    return this;
  }

  /**
   * Get name
   * @return name
   */
  
  @JsonProperty("name")
  public @Nullable String getName() {
    return name;
  }

  public void setName(@Nullable String name) {
    this.name = name;
  }

  public Device model(@Nullable String model) {
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

  public Device appVersion(@Nullable String appVersion) {
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

  public Device lastSeenAt(@Nullable OffsetDateTime lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
    return this;
  }

  /**
   * Last request seen from this installation. Nothing announces a disconnection, so this is a \"last seen\", never a presence: a client that renders \"online\" does so from a threshold of its own choosing over this value, and says \"active\" rather than claiming certainty. 
   * @return lastSeenAt
   */
  @Valid 
  @JsonProperty("last_seen_at")
  public @Nullable OffsetDateTime getLastSeenAt() {
    return lastSeenAt;
  }

  public void setLastSeenAt(@Nullable OffsetDateTime lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
  }

  public Device isCurrent(Boolean isCurrent) {
    this.isCurrent = isCurrent;
    return this;
  }

  /**
   * True on the one device whose token made this call.  This is the row the user must not revoke by accident. `DELETE /me/devices/{id}` on your own device is allowed and signs you out — a device list that cannot say which one is *this* one asks the user to find out by trying.  False everywhere else, including on the television just linked by `POST /auth/device/approve`, which is by definition not the caller. 
   * @return isCurrent
   */
  @NotNull 
  @JsonProperty("is_current")
  public Boolean getIsCurrent() {
    return isCurrent;
  }

  public void setIsCurrent(Boolean isCurrent) {
    this.isCurrent = isCurrent;
  }

  public Device createdAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /**
   * Get createdAt
   * @return createdAt
   */
  @NotNull @Valid 
  @JsonProperty("created_at")
  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Device device = (Device) o;
    return Objects.equals(this.id, device.id) &&
        Objects.equals(this.platform, device.platform) &&
        Objects.equals(this.name, device.name) &&
        Objects.equals(this.model, device.model) &&
        Objects.equals(this.appVersion, device.appVersion) &&
        Objects.equals(this.lastSeenAt, device.lastSeenAt) &&
        Objects.equals(this.isCurrent, device.isCurrent) &&
        Objects.equals(this.createdAt, device.createdAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, platform, name, model, appVersion, lastSeenAt, isCurrent, createdAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Device {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    platform: ").append(toIndentedString(platform)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    model: ").append(toIndentedString(model)).append("\n");
    sb.append("    appVersion: ").append(toIndentedString(appVersion)).append("\n");
    sb.append("    lastSeenAt: ").append(toIndentedString(lastSeenAt)).append("\n");
    sb.append("    isCurrent: ").append(toIndentedString(isCurrent)).append("\n");
    sb.append("    createdAt: ").append(toIndentedString(createdAt)).append("\n");
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

