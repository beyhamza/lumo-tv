package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.FieldError;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * RFC 7807 problem detail. Every error response in this API uses this shape with &#x60;Content-Type: application/problem+json&#x60;.  Clients switch on &#x60;code&#x60;, never on &#x60;title&#x60; or &#x60;detail&#x60;. &#x60;detail&#x60; is written for logs and support, is not localised, and is never displayed raw to a user. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class Problem {

  private URI type;

  private String title;

  private Integer status;

  private ErrorCode code;

  private @Nullable String detail = null;

  private @Nullable String instance = null;

  @Valid
  private @Nullable List<@Valid FieldError> errors;

  public Problem() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public Problem(URI type, String title, Integer status, ErrorCode code) {
    this.type = type;
    this.title = title;
    this.status = status;
    this.code = code;
  }

  public Problem type(URI type) {
    this.type = type;
    return this;
  }

  /**
   * Stable URI identifying the problem type.
   * @return type
   */
  @NotNull @Valid 
  @JsonProperty("type")
  public URI getType() {
    return type;
  }

  public void setType(URI type) {
    this.type = type;
  }

  public Problem title(String title) {
    this.title = title;
    return this;
  }

  /**
   * Short, human-readable, English, non-localised summary.
   * @return title
   */
  @NotNull 
  @JsonProperty("title")
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Problem status(Integer status) {
    this.status = status;
    return this;
  }

  /**
   * HTTP status code, repeated for convenience.
   * minimum: 100
   * maximum: 599
   * @return status
   */
  @NotNull @Min(100) @Max(599) 
  @JsonProperty("status")
  public Integer getStatus() {
    return status;
  }

  public void setStatus(Integer status) {
    this.status = status;
  }

  public Problem code(ErrorCode code) {
    this.code = code;
    return this;
  }

  /**
   * Stable machine-readable code. **This is the only field a client is allowed to branch on.** Clients must handle an unrecognised value gracefully rather than crash: new codes may be added within v1. 
   * @return code
   */
  @NotNull @Valid 
  @JsonProperty("code")
  public ErrorCode getCode() {
    return code;
  }

  public void setCode(ErrorCode code) {
    this.code = code;
  }

  public Problem detail(@Nullable String detail) {
    this.detail = detail;
    return this;
  }

  /**
   * Diagnostic explanation for logs and support. Never rendered raw in the UI. Never contains a stream URL, an Xtream password or a token. 
   * @return detail
   */
  
  @JsonProperty("detail")
  public @Nullable String getDetail() {
    return detail;
  }

  public void setDetail(@Nullable String detail) {
    this.detail = detail;
  }

  public Problem instance(@Nullable String instance) {
    this.instance = instance;
    return this;
  }

  /**
   * Path of the request that produced this problem.
   * @return instance
   */
  
  @JsonProperty("instance")
  public @Nullable String getInstance() {
    return instance;
  }

  public void setInstance(@Nullable String instance) {
    this.instance = instance;
  }

  public Problem errors(@Nullable List<@Valid FieldError> errors) {
    this.errors = errors;
    return this;
  }

  public Problem addErrorsItem(FieldError errorsItem) {
    if (this.errors == null) {
      this.errors = new ArrayList<>();
    }
    this.errors.add(errorsItem);
    return this;
  }

  /**
   * Per-field details, present on validation failures only.
   * @return errors
   */
  @Valid 
  @JsonProperty("errors")
  public @Nullable List<@Valid FieldError> getErrors() {
    return errors;
  }

  public void setErrors(@Nullable List<@Valid FieldError> errors) {
    this.errors = errors;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Problem problem = (Problem) o;
    return Objects.equals(this.type, problem.type) &&
        Objects.equals(this.title, problem.title) &&
        Objects.equals(this.status, problem.status) &&
        Objects.equals(this.code, problem.code) &&
        Objects.equals(this.detail, problem.detail) &&
        Objects.equals(this.instance, problem.instance) &&
        Objects.equals(this.errors, problem.errors);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, title, status, code, detail, instance, errors);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Problem {\n");
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    code: ").append(toIndentedString(code)).append("\n");
    sb.append("    detail: ").append(toIndentedString(detail)).append("\n");
    sb.append("    instance: ").append(toIndentedString(instance)).append("\n");
    sb.append("    errors: ").append(toIndentedString(errors)).append("\n");
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

