package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.net.URI;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * RFC 8628 device authorization response. Property names are the RFC&#39;s, verbatim. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class DeviceCodeResponse {

  private String deviceCode;

  private String userCode;

  private URI verificationUri;

  private URI verificationUriComplete;

  private Integer expiresIn;

  private Integer interval;

  public DeviceCodeResponse() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public DeviceCodeResponse(String deviceCode, String userCode, URI verificationUri, URI verificationUriComplete, Integer expiresIn, Integer interval) {
    this.deviceCode = deviceCode;
    this.userCode = userCode;
    this.verificationUri = verificationUri;
    this.verificationUriComplete = verificationUriComplete;
    this.expiresIn = expiresIn;
    this.interval = interval;
  }

  public DeviceCodeResponse deviceCode(String deviceCode) {
    this.deviceCode = deviceCode;
    return this;
  }

  /**
   * Secret held by the television and sent on every poll. Never displayed. Stored hashed server-side (`device_authorization.device_code_hash`). 
   * @return deviceCode
   */
  @NotNull 
  @JsonProperty("device_code")
  public String getDeviceCode() {
    return deviceCode;
  }

  public void setDeviceCode(String deviceCode) {
    this.deviceCode = deviceCode;
  }

  public DeviceCodeResponse userCode(String userCode) {
    this.userCode = userCode;
    return this;
  }

  /**
   * The 8 characters shown on screen and typed on `lumo.tv/activate`.  Drawn from an alphabet with no ambiguous glyphs: `0`/`O` and `1`/`I`/`L` are all excluded, so what is read at three metres is what gets typed. Transmitted unformatted; a client may insert a separator for display, and the server accepts the code case-insensitively with separators and spaces stripped.  Single-use, valid 10 minutes. 
   * @return userCode
   */
  @NotNull @Pattern(regexp = "^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$") @Size(min = 8, max = 8) 
  @JsonProperty("user_code")
  public String getUserCode() {
    return userCode;
  }

  public void setUserCode(String userCode) {
    this.userCode = userCode;
  }

  public DeviceCodeResponse verificationUri(URI verificationUri) {
    this.verificationUri = verificationUri;
    return this;
  }

  /**
   * Page to open on a phone or a computer.
   * @return verificationUri
   */
  @NotNull @Valid 
  @JsonProperty("verification_uri")
  public URI getVerificationUri() {
    return verificationUri;
  }

  public void setVerificationUri(URI verificationUri) {
    this.verificationUri = verificationUri;
  }

  public DeviceCodeResponse verificationUriComplete(URI verificationUriComplete) {
    this.verificationUriComplete = verificationUriComplete;
    return this;
  }

  /**
   * Same page with the code pre-filled — this is what the QR code on the television encodes, so the nominal path involves no typing at all. 
   * @return verificationUriComplete
   */
  @NotNull @Valid 
  @JsonProperty("verification_uri_complete")
  public URI getVerificationUriComplete() {
    return verificationUriComplete;
  }

  public void setVerificationUriComplete(URI verificationUriComplete) {
    this.verificationUriComplete = verificationUriComplete;
  }

  public DeviceCodeResponse expiresIn(Integer expiresIn) {
    this.expiresIn = expiresIn;
    return this;
  }

  /**
   * Lifetime of the authorization, in seconds.
   * @return expiresIn
   */
  @NotNull 
  @JsonProperty("expires_in")
  public Integer getExpiresIn() {
    return expiresIn;
  }

  public void setExpiresIn(Integer expiresIn) {
    this.expiresIn = expiresIn;
  }

  public DeviceCodeResponse interval(Integer interval) {
    this.interval = interval;
    return this;
  }

  /**
   * Minimum seconds between two polls of `/auth/device/token`. Polling faster earns `SLOW_DOWN`, after which the client adds 5 seconds. 
   * @return interval
   */
  @NotNull 
  @JsonProperty("interval")
  public Integer getInterval() {
    return interval;
  }

  public void setInterval(Integer interval) {
    this.interval = interval;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DeviceCodeResponse deviceCodeResponse = (DeviceCodeResponse) o;
    return Objects.equals(this.deviceCode, deviceCodeResponse.deviceCode) &&
        Objects.equals(this.userCode, deviceCodeResponse.userCode) &&
        Objects.equals(this.verificationUri, deviceCodeResponse.verificationUri) &&
        Objects.equals(this.verificationUriComplete, deviceCodeResponse.verificationUriComplete) &&
        Objects.equals(this.expiresIn, deviceCodeResponse.expiresIn) &&
        Objects.equals(this.interval, deviceCodeResponse.interval);
  }

  @Override
  public int hashCode() {
    return Objects.hash(deviceCode, userCode, verificationUri, verificationUriComplete, expiresIn, interval);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeviceCodeResponse {\n");
    sb.append("    deviceCode: ").append("*").append("\n");
    sb.append("    userCode: ").append(toIndentedString(userCode)).append("\n");
    sb.append("    verificationUri: ").append(toIndentedString(verificationUri)).append("\n");
    sb.append("    verificationUriComplete: ").append(toIndentedString(verificationUriComplete)).append("\n");
    sb.append("    expiresIn: ").append(toIndentedString(expiresIn)).append("\n");
    sb.append("    interval: ").append(toIndentedString(interval)).append("\n");
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

