package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.Locale;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Optional body. The portal needs nothing but the authenticated caller; &#x60;locale&#x60; is here for the same reason as on checkout. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class CreatePortalSessionRequest {

  private @Nullable Locale locale;

  public CreatePortalSessionRequest locale(@Nullable Locale locale) {
    this.locale = locale;
    return this;
  }

  /**
   * Language the portal renders in. Defaults to the user's `locale`.
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CreatePortalSessionRequest createPortalSessionRequest = (CreatePortalSessionRequest) o;
    return Objects.equals(this.locale, createPortalSessionRequest.locale);
  }

  @Override
  public int hashCode() {
    return Objects.hash(locale);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CreatePortalSessionRequest {\n");
    sb.append("    locale: ").append(toIndentedString(locale)).append("\n");
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

