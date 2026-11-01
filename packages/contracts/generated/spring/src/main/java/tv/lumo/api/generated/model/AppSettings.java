package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * AppSettings
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class AppSettings {

  private String supportEmail = null;

  private Boolean registrationOpen;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime updatedAt;

  public AppSettings() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public AppSettings(String supportEmail, Boolean registrationOpen, OffsetDateTime updatedAt) {
    this.supportEmail = supportEmail;
    this.registrationOpen = registrationOpen;
    this.updatedAt = updatedAt;
  }

  public AppSettings supportEmail(String supportEmail) {
    this.supportEmail = supportEmail;
    return this;
  }

  /**
   * Get supportEmail
   * @return supportEmail
   */
  @NotNull @jakarta.validation.constraints.Email 
  @JsonProperty("support_email")
  public String getSupportEmail() {
    return supportEmail;
  }

  public void setSupportEmail(String supportEmail) {
    this.supportEmail = supportEmail;
  }

  public AppSettings registrationOpen(Boolean registrationOpen) {
    this.registrationOpen = registrationOpen;
    return this;
  }

  /**
   * Whether new accounts can be created. Closing it answers `403 REGISTRATION_CLOSED` to `POST /auth/register`, and to a first Google sign-in; existing accounts keep working. 
   * @return registrationOpen
   */
  @NotNull 
  @JsonProperty("registration_open")
  public Boolean getRegistrationOpen() {
    return registrationOpen;
  }

  public void setRegistrationOpen(Boolean registrationOpen) {
    this.registrationOpen = registrationOpen;
  }

  public AppSettings updatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  /**
   * Get updatedAt
   * @return updatedAt
   */
  @NotNull @Valid 
  @JsonProperty("updated_at")
  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AppSettings appSettings = (AppSettings) o;
    return Objects.equals(this.supportEmail, appSettings.supportEmail) &&
        Objects.equals(this.registrationOpen, appSettings.registrationOpen) &&
        Objects.equals(this.updatedAt, appSettings.updatedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(supportEmail, registrationOpen, updatedAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AppSettings {\n");
    sb.append("    supportEmail: ").append(toIndentedString(supportEmail)).append("\n");
    sb.append("    registrationOpen: ").append(toIndentedString(registrationOpen)).append("\n");
    sb.append("    updatedAt: ").append(toIndentedString(updatedAt)).append("\n");
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

