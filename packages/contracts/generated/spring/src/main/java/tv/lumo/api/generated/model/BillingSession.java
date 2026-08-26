package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * A redirect target on Stripe&#39;s side. Not a grant of anything: the entitlement moves when the webhook writes it, and only then. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class BillingSession {

  private String url;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private @Nullable OffsetDateTime expiresAt = null;

  public BillingSession() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public BillingSession(String url) {
    this.url = url;
  }

  public BillingSession url(String url) {
    this.url = url;
    return this;
  }

  /**
   * **Sensitive.** Typed `format: password` rather than `format: uri` for the same reason as `PlaybackInfo.stream_url`: both are plain strings, but `password` makes every generator mask the property in `toString()`.  This URL is a bearer capability. Whoever holds it reaches a session opened for this account — on the portal, that means invoices, the payment method and the cancel button. It must not reach a log line at any level, `DEBUG` included (AGENTS.md §5).  Single-use and short-lived. Redirect to it immediately; never store it, never put it in a query parameter of your own, never email it. 
   * @return url
   */
  @NotNull 
  @JsonProperty("url")
  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public BillingSession expiresAt(@Nullable OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  /**
   * When the session stops being redeemable, when Stripe reports it. Null means unknown; the client redirects immediately either way rather than holding the URL. 
   * @return expiresAt
   */
  @Valid 
  @JsonProperty("expires_at")
  public @Nullable OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(@Nullable OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BillingSession billingSession = (BillingSession) o;
    return Objects.equals(this.url, billingSession.url) &&
        Objects.equals(this.expiresAt, billingSession.expiresAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(url, expiresAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class BillingSession {\n");
    sb.append("    url: ").append("*").append("\n");
    sb.append("    expiresAt: ").append(toIndentedString(expiresAt)).append("\n");
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

