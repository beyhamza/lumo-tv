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
 * Replaces the whole configuration. Without &#x60;support_email&#x60;, none is set.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class UpdateAppSettingsRequest {

  private @Nullable String supportEmail = null;

  private Boolean registrationOpen;

  public UpdateAppSettingsRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public UpdateAppSettingsRequest(Boolean registrationOpen) {
    this.registrationOpen = registrationOpen;
  }

  public UpdateAppSettingsRequest supportEmail(@Nullable String supportEmail) {
    this.supportEmail = supportEmail;
    return this;
  }

  /**
   * Get supportEmail
   * @return supportEmail
   */
  @Size(max = 254) @jakarta.validation.constraints.Email 
  @JsonProperty("support_email")
  public @Nullable String getSupportEmail() {
    return supportEmail;
  }

  public void setSupportEmail(@Nullable String supportEmail) {
    this.supportEmail = supportEmail;
  }

  public UpdateAppSettingsRequest registrationOpen(Boolean registrationOpen) {
    this.registrationOpen = registrationOpen;
    return this;
  }

  /**
   * Get registrationOpen
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UpdateAppSettingsRequest updateAppSettingsRequest = (UpdateAppSettingsRequest) o;
    return Objects.equals(this.supportEmail, updateAppSettingsRequest.supportEmail) &&
        Objects.equals(this.registrationOpen, updateAppSettingsRequest.registrationOpen);
  }

  @Override
  public int hashCode() {
    return Objects.hash(supportEmail, registrationOpen);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class UpdateAppSettingsRequest {\n");
    sb.append("    supportEmail: ").append(toIndentedString(supportEmail)).append("\n");
    sb.append("    registrationOpen: ").append(toIndentedString(registrationOpen)).append("\n");
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

