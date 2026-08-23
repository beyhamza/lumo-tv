package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.DeviceRegistration;
import tv.lumo.api.generated.model.Locale;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * RegisterRequest
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class RegisterRequest {

  private String email;

  private String password;

  private @Nullable String displayName = null;

  private @Nullable Locale locale;

  private DeviceRegistration device;

  public RegisterRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public RegisterRequest(String email, String password, DeviceRegistration device) {
    this.email = email;
    this.password = password;
    this.device = device;
  }

  public RegisterRequest email(String email) {
    this.email = email;
    return this;
  }

  /**
   * Get email
   * @return email
   */
  @NotNull @Size(max = 254) @jakarta.validation.constraints.Email 
  @JsonProperty("email")
  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public RegisterRequest password(String password) {
    this.password = password;
    return this;
  }

  /**
   * At least 10 characters. Strength is measured by entropy (zxcvbn), not by composition rules; clients show the unmet rule before submission rather than after (US-01). Hashed with Argon2id. 
   * @return password
   */
  @NotNull @Size(min = 10, max = 200) 
  @JsonProperty("password")
  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public RegisterRequest displayName(@Nullable String displayName) {
    this.displayName = displayName;
    return this;
  }

  /**
   * Get displayName
   * @return displayName
   */
  @Size(max = 100) 
  @JsonProperty("display_name")
  public @Nullable String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(@Nullable String displayName) {
    this.displayName = displayName;
  }

  public RegisterRequest locale(@Nullable Locale locale) {
    this.locale = locale;
    return this;
  }

  /**
   * Defaults to the `Accept-Language` header, then to `en`.
   * @return locale
   */
  @Valid 
  @JsonProperty("locale")
  public @Nullable Locale getLocale() {
    return locale;
  }

  public void setLocale(@Nullable Locale locale) {
    this.locale = locale;
  }

  public RegisterRequest device(DeviceRegistration device) {
    this.device = device;
    return this;
  }

  /**
   * Get device
   * @return device
   */
  @NotNull @Valid 
  @JsonProperty("device")
  public DeviceRegistration getDevice() {
    return device;
  }

  public void setDevice(DeviceRegistration device) {
    this.device = device;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RegisterRequest registerRequest = (RegisterRequest) o;
    return Objects.equals(this.email, registerRequest.email) &&
        Objects.equals(this.password, registerRequest.password) &&
        Objects.equals(this.displayName, registerRequest.displayName) &&
        Objects.equals(this.locale, registerRequest.locale) &&
        Objects.equals(this.device, registerRequest.device);
  }

  @Override
  public int hashCode() {
    return Objects.hash(email, password, displayName, locale, device);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class RegisterRequest {\n");
    sb.append("    email: ").append(toIndentedString(email)).append("\n");
    sb.append("    password: ").append("*").append("\n");
    sb.append("    displayName: ").append(toIndentedString(displayName)).append("\n");
    sb.append("    locale: ").append(toIndentedString(locale)).append("\n");
    sb.append("    device: ").append(toIndentedString(device)).append("\n");
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

