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
 * One field-level validation failure.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class FieldError {

  private String field;

  private String code;

  private @Nullable String detail = null;

  public FieldError() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public FieldError(String field, String code) {
    this.field = field;
    this.code = code;
  }

  public FieldError field(String field) {
    this.field = field;
    return this;
  }

  /**
   * JSON pointer to the offending property, relative to the request body.
   * @return field
   */
  @NotNull 
  @JsonProperty("field")
  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  public FieldError code(String code) {
    this.code = code;
    return this;
  }

  /**
   * Stable machine-readable reason.
   * @return code
   */
  @NotNull 
  @JsonProperty("code")
  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public FieldError detail(@Nullable String detail) {
    this.detail = detail;
    return this;
  }

  /**
   * Diagnostic explanation. Not localised, not for display.
   * @return detail
   */
  
  @JsonProperty("detail")
  public @Nullable String getDetail() {
    return detail;
  }

  public void setDetail(@Nullable String detail) {
    this.detail = detail;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FieldError fieldError = (FieldError) o;
    return Objects.equals(this.field, fieldError.field) &&
        Objects.equals(this.code, fieldError.code) &&
        Objects.equals(this.detail, fieldError.detail);
  }

  @Override
  public int hashCode() {
    return Objects.hash(field, code, detail);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class FieldError {\n");
    sb.append("    field: ").append(toIndentedString(field)).append("\n");
    sb.append("    code: ").append(toIndentedString(code)).append("\n");
    sb.append("    detail: ").append(toIndentedString(detail)).append("\n");
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

