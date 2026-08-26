package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.Locale;
import tv.lumo.api.generated.model.Plan;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * What the caller is allowed to influence when opening a checkout. Which is: almost nothing, deliberately. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class CreateCheckoutSessionRequest {

  private Plan plan;

  private @Nullable Locale locale;

  public CreateCheckoutSessionRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public CreateCheckoutSessionRequest(Plan plan) {
    this.plan = plan;
  }

  public CreateCheckoutSessionRequest plan(Plan plan) {
    this.plan = plan;
    return this;
  }

  /**
   * The tier being bought. `PREMIUM` is the only purchasable value; a request for `FREE` is `400` `VALIDATION_FAILED`.  The property exists rather than being implied so that a second paid tier does not change the shape of this operation. 
   * @return plan
   */
  @NotNull @Valid 
  @JsonProperty("plan")
  public Plan getPlan() {
    return plan;
  }

  public void setPlan(Plan plan) {
    this.plan = plan;
  }

  public CreateCheckoutSessionRequest locale(@Nullable Locale locale) {
    this.locale = locale;
    return this;
  }

  /**
   * Language Stripe renders the checkout in. Defaults to the user's `locale`. 
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
    CreateCheckoutSessionRequest createCheckoutSessionRequest = (CreateCheckoutSessionRequest) o;
    return Objects.equals(this.plan, createCheckoutSessionRequest.plan) &&
        Objects.equals(this.locale, createCheckoutSessionRequest.locale);
  }

  @Override
  public int hashCode() {
    return Objects.hash(plan, locale);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CreateCheckoutSessionRequest {\n");
    sb.append("    plan: ").append(toIndentedString(plan)).append("\n");
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

