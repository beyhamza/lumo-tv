package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * A freshly issued access/refresh pair. The refresh token supersedes any previously held one for this device. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class TokenPair {

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

  public TokenPair() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public TokenPair(String accessToken, TokenTypeEnum tokenType, Integer expiresIn, String refreshToken) {
    this.accessToken = accessToken;
    this.tokenType = tokenType;
    this.expiresIn = expiresIn;
    this.refreshToken = refreshToken;
  }

  public TokenPair accessToken(String accessToken) {
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

  public TokenPair tokenType(TokenTypeEnum tokenType) {
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

  public TokenPair expiresIn(Integer expiresIn) {
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

  public TokenPair refreshToken(String refreshToken) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TokenPair tokenPair = (TokenPair) o;
    return Objects.equals(this.accessToken, tokenPair.accessToken) &&
        Objects.equals(this.tokenType, tokenPair.tokenType) &&
        Objects.equals(this.expiresIn, tokenPair.expiresIn) &&
        Objects.equals(this.refreshToken, tokenPair.refreshToken);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accessToken, tokenType, expiresIn, refreshToken);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class TokenPair {\n");
    sb.append("    accessToken: ").append("*").append("\n");
    sb.append("    tokenType: ").append(toIndentedString(tokenType)).append("\n");
    sb.append("    expiresIn: ").append(toIndentedString(expiresIn)).append("\n");
    sb.append("    refreshToken: ").append("*").append("\n");
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

