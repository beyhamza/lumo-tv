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
 * ResetPasswordRequest
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class ResetPasswordRequest {

  private String token;

  private String password;

  public ResetPasswordRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public ResetPasswordRequest(String token, String password) {
    this.token = token;
    this.password = password;
  }

  public ResetPasswordRequest token(String token) {
    this.token = token;
    return this;
  }

  /**
   * Single-use token from the reset email. Masked in generated `toString()`.
   * @return token
   */
  @NotNull @Size(min = 16, max = 512) 
  @JsonProperty("token")
  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public ResetPasswordRequest password(String password) {
    this.password = password;
    return this;
  }

  /**
   * Get password
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ResetPasswordRequest resetPasswordRequest = (ResetPasswordRequest) o;
    return Objects.equals(this.token, resetPasswordRequest.token) &&
        Objects.equals(this.password, resetPasswordRequest.password);
  }

  @Override
  public int hashCode() {
    return Objects.hash(token, password);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ResetPasswordRequest {\n");
    sb.append("    token: ").append("*").append("\n");
    sb.append("    password: ").append("*").append("\n");
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

