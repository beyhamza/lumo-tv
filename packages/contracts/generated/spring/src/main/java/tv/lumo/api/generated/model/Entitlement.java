package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.EntitlementProvider;
import tv.lumo.api.generated.model.EntitlementStatus;
import tv.lumo.api.generated.model.Plan;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * The user&#39;s access rights, computed server-side. &#x60;provider_ref&#x60; is a billing-internal reference and is intentionally not exposed. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class Entitlement {

  private Plan plan;

  private EntitlementStatus status;

  private EntitlementProvider provider;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private @Nullable OffsetDateTime currentPeriodEnd = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private @Nullable OffsetDateTime trialEndsAt = null;

  private @Nullable Integer maxSources = null;

  private @Nullable Integer maxDevices = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime updatedAt;

  public Entitlement() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public Entitlement(Plan plan, EntitlementStatus status, EntitlementProvider provider, OffsetDateTime updatedAt) {
    this.plan = plan;
    this.status = status;
    this.provider = provider;
    this.updatedAt = updatedAt;
  }

  public Entitlement plan(Plan plan) {
    this.plan = plan;
    return this;
  }

  /**
   * Get plan
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

  public Entitlement status(EntitlementStatus status) {
    this.status = status;
    return this;
  }

  /**
   * Get status
   * @return status
   */
  @NotNull @Valid 
  @JsonProperty("status")
  public EntitlementStatus getStatus() {
    return status;
  }

  public void setStatus(EntitlementStatus status) {
    this.status = status;
  }

  public Entitlement provider(EntitlementProvider provider) {
    this.provider = provider;
    return this;
  }

  /**
   * Get provider
   * @return provider
   */
  @NotNull @Valid 
  @JsonProperty("provider")
  public EntitlementProvider getProvider() {
    return provider;
  }

  public void setProvider(EntitlementProvider provider) {
    this.provider = provider;
  }

  public Entitlement currentPeriodEnd(@Nullable OffsetDateTime currentPeriodEnd) {
    this.currentPeriodEnd = currentPeriodEnd;
    return this;
  }

  /**
   * End of the paid period. Null on `FREE`.
   * @return currentPeriodEnd
   */
  @Valid 
  @JsonProperty("current_period_end")
  public @Nullable OffsetDateTime getCurrentPeriodEnd() {
    return currentPeriodEnd;
  }

  public void setCurrentPeriodEnd(@Nullable OffsetDateTime currentPeriodEnd) {
    this.currentPeriodEnd = currentPeriodEnd;
  }

  public Entitlement trialEndsAt(@Nullable OffsetDateTime trialEndsAt) {
    this.trialEndsAt = trialEndsAt;
    return this;
  }

  /**
   * End of the free trial. Non-null only while `status` is `TRIALING`. Distinct from `current_period_end`, which dates the end of a period that was *paid for*. 
   * @return trialEndsAt
   */
  @Valid 
  @JsonProperty("trial_ends_at")
  public @Nullable OffsetDateTime getTrialEndsAt() {
    return trialEndsAt;
  }

  public void setTrialEndsAt(@Nullable OffsetDateTime trialEndsAt) {
    this.trialEndsAt = trialEndsAt;
  }

  public Entitlement maxSources(@Nullable Integer maxSources) {
    this.maxSources = maxSources;
    return this;
  }

  /**
   * Sources this plan allows. **Null means unlimited**, not unknown.  Present so that a client can disable \"add a source\" before the user fills a form that is going to be refused, and so that the free plan's ceiling lives in exactly one place. A client never carries its own copy of this number: that would be an access right computed client-side, which this project forbids outright (AGENTS.md §1). The day the free plan allows two, one row changes here and three applications follow without a release. 
   * minimum: 1
   * @return maxSources
   */
  @Min(1) 
  @JsonProperty("max_sources")
  public @Nullable Integer getMaxSources() {
    return maxSources;
  }

  public void setMaxSources(@Nullable Integer maxSources) {
    this.maxSources = maxSources;
  }

  public Entitlement maxDevices(@Nullable Integer maxDevices) {
    this.maxDevices = maxDevices;
    return this;
  }

  /**
   * Devices this plan allows. **Null means unlimited**, not unknown. Same rule as `max_sources`: read, never assumed. 
   * minimum: 1
   * @return maxDevices
   */
  @Min(1) 
  @JsonProperty("max_devices")
  public @Nullable Integer getMaxDevices() {
    return maxDevices;
  }

  public void setMaxDevices(@Nullable Integer maxDevices) {
    this.maxDevices = maxDevices;
  }

  public Entitlement updatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  /**
   * Get updatedAt
   * @return updatedAt
   */
  @NotNull @Valid 
  @JsonProperty("updated_at")
  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Entitlement entitlement = (Entitlement) o;
    return Objects.equals(this.plan, entitlement.plan) &&
        Objects.equals(this.status, entitlement.status) &&
        Objects.equals(this.provider, entitlement.provider) &&
        Objects.equals(this.currentPeriodEnd, entitlement.currentPeriodEnd) &&
        Objects.equals(this.trialEndsAt, entitlement.trialEndsAt) &&
        Objects.equals(this.maxSources, entitlement.maxSources) &&
        Objects.equals(this.maxDevices, entitlement.maxDevices) &&
        Objects.equals(this.updatedAt, entitlement.updatedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(plan, status, provider, currentPeriodEnd, trialEndsAt, maxSources, maxDevices, updatedAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Entitlement {\n");
    sb.append("    plan: ").append(toIndentedString(plan)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    provider: ").append(toIndentedString(provider)).append("\n");
    sb.append("    currentPeriodEnd: ").append(toIndentedString(currentPeriodEnd)).append("\n");
    sb.append("    trialEndsAt: ").append(toIndentedString(trialEndsAt)).append("\n");
    sb.append("    maxSources: ").append(toIndentedString(maxSources)).append("\n");
    sb.append("    maxDevices: ").append(toIndentedString(maxDevices)).append("\n");
    sb.append("    updatedAt: ").append(toIndentedString(updatedAt)).append("\n");
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

