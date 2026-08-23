package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.UUID;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.User;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * AuthSession
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class AuthSession {

  private String accessToken;

  /**
   * Gets or Sets tokenType
   */
  public enum TokenTypeEnum {
    BEARER("Bearer");

    private final String value;

    TokenTypeEnum(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static TokenTypeEnum fromValue(String value) {
      for (TokenTypeEnum b : TokenTypeEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  private TokenTypeEnum tokenType;

  private Integer expiresIn;

  private String refreshToken;

  private User user;

  private UUID deviceId;

  public AuthSession() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public AuthSession(String accessToken, TokenTypeEnum tokenType, Integer expiresIn, String refreshToken, User user, UUID deviceId) {
    this.accessToken = accessToken;
    this.tokenType = tokenType;
    this.expiresIn = expiresIn;
    this.refreshToken = refreshToken;
    this.user = user;
    this.deviceId = deviceId;
  }

  public AuthSession accessToken(String accessToken) {
    this.accessToken = accessToken;
    return this;
  }

  /**
   * Bearer JWT, valid 15 minutes. `format: password` carries no UI meaning here: it makes the generators mask this property in `toString()`, so a token cannot reach a log line by accident (AGENTS.md §5). 
   * @return accessToken
   */
  @NotNull 
  @JsonProperty("access_token")
  public String getAccessToken() {
    return accessToken;
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }

  public AuthSession tokenType(TokenTypeEnum tokenType) {
    this.tokenType = tokenType;
    return this;
  }

  /**
   * Get tokenType
   * @return tokenType
   */
  @NotNull 
  @JsonProperty("token_type")
  public TokenTypeEnum getTokenType() {
    return tokenType;
  }

  public void setTokenType(TokenTypeEnum tokenType) {
    this.tokenType = tokenType;
  }

  public AuthSession expiresIn(Integer expiresIn) {
    this.expiresIn = expiresIn;
    return this;
  }

  /**
   * Lifetime of `access_token`, in seconds.
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

  public AuthSession refreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
    return this;
  }

  /**
   * Opaque token, single-use. Masked in generated `toString()`. Rotated on every `/auth/refresh`. Presenting a consumed one revokes the device's entire chain. 
   * @return refreshToken
   */
  @NotNull 
  @JsonProperty("refresh_token")
  public String getRefreshToken() {
    return refreshToken;
  }

  public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  public AuthSession user(User user) {
    this.user = user;
    return this;
  }

  /**
   * Get user
   * @return user
   */
  @NotNull @Valid 
  @JsonProperty("user")
  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public AuthSession deviceId(UUID deviceId) {
    this.deviceId = deviceId;
    return this;
  }

  /**
   * The `device` this session is bound to.
   * @return deviceId
   */
  @NotNull @Valid 
  @JsonProperty("device_id")
  public UUID getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(UUID deviceId) {
    this.deviceId = deviceId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AuthSession authSession = (AuthSession) o;
    return Objects.equals(this.accessToken, authSession.accessToken) &&
        Objects.equals(this.tokenType, authSession.tokenType) &&
        Objects.equals(this.expiresIn, authSession.expiresIn) &&
        Objects.equals(this.refreshToken, authSession.refreshToken) &&
        Objects.equals(this.user, authSession.user) &&
        Objects.equals(this.deviceId, authSession.deviceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accessToken, tokenType, expiresIn, refreshToken, user, deviceId);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AuthSession {\n");
    sb.append("    accessToken: ").append("*").append("\n");
    sb.append("    tokenType: ").append(toIndentedString(tokenType)).append("\n");
    sb.append("    expiresIn: ").append(toIndentedString(expiresIn)).append("\n");
    sb.append("    refreshToken: ").append("*").append("\n");
    sb.append("    user: ").append(toIndentedString(user)).append("\n");
    sb.append("    deviceId: ").append(toIndentedString(deviceId)).append("\n");
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

