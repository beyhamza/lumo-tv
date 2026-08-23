package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Every stable error code this API emits, ingestion codes included.  Grouped by concern:  - **Generic** — `VALIDATION_FAILED`, `UNAUTHENTICATED`,   `ACCESS_TOKEN_EXPIRED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`,   `RATE_LIMITED`, `INTERNAL_ERROR`. - **Account and sign-in** — `EMAIL_ALREADY_REGISTERED`,   `INVALID_CREDENTIALS`, `PASSWORD_TOO_WEAK`, `OAUTH_TOKEN_INVALID`,   `VERIFICATION_TOKEN_INVALID`, `VERIFICATION_TOKEN_EXPIRED`,   `RESET_TOKEN_INVALID`, `RESET_TOKEN_EXPIRED`. - **Sessions** — `REFRESH_TOKEN_INVALID`, `REFRESH_TOKEN_REUSED`,   `DEVICE_NOT_FOUND`. - **TV activation (RFC 8628)** — `AUTHORIZATION_PENDING`, `SLOW_DOWN`,   `ACCESS_DENIED`, `EXPIRED_TOKEN`, `DEVICE_CODE_NOT_FOUND`,   `DEVICE_CODE_EXPIRED`, `DEVICE_CODE_ALREADY_USED`. - **Sources and ingestion** — `SOURCE_NOT_FOUND`, `SOURCE_NOT_READY`,   `SOURCE_SYNC_IN_PROGRESS`, `SOURCE_SYNC_RATE_LIMITED`, plus every   `IngestionErrorCode`. - **Catalogue and user data** — `CHANNEL_NOT_FOUND`,   `FAVORITE_NOT_FOUND`, `FAVORITE_ALREADY_EXISTS`,   `FAVORITE_GROUP_NOT_FOUND`, `FAVORITE_GROUP_ALREADY_EXISTS`. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public enum ErrorCode {
  
  VALIDATION_FAILED("VALIDATION_FAILED"),
  
  UNAUTHENTICATED("UNAUTHENTICATED"),
  
  ACCESS_TOKEN_EXPIRED("ACCESS_TOKEN_EXPIRED"),
  
  FORBIDDEN("FORBIDDEN"),
  
  NOT_FOUND("NOT_FOUND"),
  
  CONFLICT("CONFLICT"),
  
  RATE_LIMITED("RATE_LIMITED"),
  
  INTERNAL_ERROR("INTERNAL_ERROR"),
  
  EMAIL_ALREADY_REGISTERED("EMAIL_ALREADY_REGISTERED"),
  
  INVALID_CREDENTIALS("INVALID_CREDENTIALS"),
  
  PASSWORD_TOO_WEAK("PASSWORD_TOO_WEAK"),
  
  OAUTH_TOKEN_INVALID("OAUTH_TOKEN_INVALID"),
  
  VERIFICATION_TOKEN_INVALID("VERIFICATION_TOKEN_INVALID"),
  
  VERIFICATION_TOKEN_EXPIRED("VERIFICATION_TOKEN_EXPIRED"),
  
  RESET_TOKEN_INVALID("RESET_TOKEN_INVALID"),
  
  RESET_TOKEN_EXPIRED("RESET_TOKEN_EXPIRED"),
  
  REFRESH_TOKEN_INVALID("REFRESH_TOKEN_INVALID"),
  
  REFRESH_TOKEN_REUSED("REFRESH_TOKEN_REUSED"),
  
  DEVICE_NOT_FOUND("DEVICE_NOT_FOUND"),
  
  AUTHORIZATION_PENDING("AUTHORIZATION_PENDING"),
  
  SLOW_DOWN("SLOW_DOWN"),
  
  ACCESS_DENIED("ACCESS_DENIED"),
  
  EXPIRED_TOKEN("EXPIRED_TOKEN"),
  
  DEVICE_CODE_NOT_FOUND("DEVICE_CODE_NOT_FOUND"),
  
  DEVICE_CODE_EXPIRED("DEVICE_CODE_EXPIRED"),
  
  DEVICE_CODE_ALREADY_USED("DEVICE_CODE_ALREADY_USED"),
  
  SOURCE_NOT_FOUND("SOURCE_NOT_FOUND"),
  
  SOURCE_NOT_READY("SOURCE_NOT_READY"),
  
  SOURCE_SYNC_IN_PROGRESS("SOURCE_SYNC_IN_PROGRESS"),
  
  SOURCE_SYNC_RATE_LIMITED("SOURCE_SYNC_RATE_LIMITED"),
  
  SOURCE_UNREACHABLE("SOURCE_UNREACHABLE"),
  
  SOURCE_AUTH_FAILED("SOURCE_AUTH_FAILED"),
  
  SOURCE_EXPIRED("SOURCE_EXPIRED"),
  
  SOURCE_MAX_CONNECTIONS("SOURCE_MAX_CONNECTIONS"),
  
  SOURCE_INVALID_FORMAT("SOURCE_INVALID_FORMAT"),
  
  SOURCE_EMPTY("SOURCE_EMPTY"),
  
  SOURCE_TOO_LARGE("SOURCE_TOO_LARGE"),
  
  CHANNEL_NOT_FOUND("CHANNEL_NOT_FOUND"),
  
  FAVORITE_NOT_FOUND("FAVORITE_NOT_FOUND"),
  
  FAVORITE_ALREADY_EXISTS("FAVORITE_ALREADY_EXISTS"),
  
  FAVORITE_GROUP_NOT_FOUND("FAVORITE_GROUP_NOT_FOUND"),
  
  FAVORITE_GROUP_ALREADY_EXISTS("FAVORITE_GROUP_ALREADY_EXISTS");

  private final String value;

  ErrorCode(String value) {
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
  public static ErrorCode fromValue(String value) {
    for (ErrorCode b : ErrorCode.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

