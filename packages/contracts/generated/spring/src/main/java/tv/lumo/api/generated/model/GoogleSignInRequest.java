package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.DeviceRegistration;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * GoogleSignInRequest
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class GoogleSignInRequest {

  private String idToken;

  private DeviceRegistration device;

  public GoogleSignInRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public GoogleSignInRequest(String idToken, DeviceRegistration device) {
    this.idToken = idToken;
    this.device = device;
  }

  public GoogleSignInRequest idToken(String idToken) {
    this.idToken = idToken;
    return this;
  }

  /**
   * Google ID token from Credential Manager (Android) or Google Identity Services (web). The server verifies signature, `aud`, `iss` and `exp` before trusting any claim in it. 
   * @return idToken
   */
  @NotNull @Size(max = 4096) 
  @JsonProperty("id_token")
  public String getIdToken() {
    return idToken;
  }

  public void setIdToken(String idToken) {
    this.idToken = idToken;
  }

  public GoogleSignInRequest device(DeviceRegistration device) {
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
    GoogleSignInRequest googleSignInRequest = (GoogleSignInRequest) o;
    return Objects.equals(this.idToken, googleSignInRequest.idToken) &&
        Objects.equals(this.device, googleSignInRequest.device);
  }

  @Override
  public int hashCode() {
    return Objects.hash(idToken, device);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class GoogleSignInRequest {\n");
    sb.append("    idToken: ").append("*").append("\n");
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

