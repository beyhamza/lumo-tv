package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.EpgAttemptStatus;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * What this server knows about its own import of a source&#39;s guide (C1, D3). **It dates the import, never the content.** A guide imported a minute ago can carry a listing the provider stopped updating last week, and nothing in an XMLTV file says when it was published. The interface therefore says \&quot;last guide import\&quot;, and never \&quot;programmes up to date\&quot;.  No URL and no secret is here, and none will be: the guide&#39;s location stays on &#x60;Source.epg_url&#x60;, for its owner.  The product&#39;s staleness rule (US-16, D4): strictly more than 24 hours since &#x60;last_successful_import_at&#x60; is old, 24 hours exactly is not yet. The age is &#x60;generated_at - last_successful_import_at&#x60; at the moment the answer is produced, then advances with the client&#39;s own clock. It informs; it blocks neither the grid nor the channel. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class EpgImportStatus {

  private Boolean configured;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime lastSuccessfulImportAt = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime lastAttemptStartedAt = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime lastAttemptFinishedAt = null;

  private EpgAttemptStatus lastAttemptStatus;

  public EpgImportStatus() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public EpgImportStatus(Boolean configured, OffsetDateTime lastSuccessfulImportAt, OffsetDateTime lastAttemptStartedAt, OffsetDateTime lastAttemptFinishedAt, EpgAttemptStatus lastAttemptStatus) {
    this.configured = configured;
    this.lastSuccessfulImportAt = lastSuccessfulImportAt;
    this.lastAttemptStartedAt = lastAttemptStartedAt;
    this.lastAttemptFinishedAt = lastAttemptFinishedAt;
    this.lastAttemptStatus = lastAttemptStatus;
  }

  public EpgImportStatus configured(Boolean configured) {
    this.configured = configured;
    return this;
  }

  /**
   * Whether the source has a guide URL at all. False means every programme list under it is empty by construction, and the client explains that rather than reporting a guide with nothing in it. 
   * @return configured
   */
  @NotNull 
  @JsonProperty("configured")
  public Boolean getConfigured() {
    return configured;
  }

  public void setConfigured(Boolean configured) {
    this.configured = configured;
  }

  public EpgImportStatus lastSuccessfulImportAt(OffsetDateTime lastSuccessfulImportAt) {
    this.lastSuccessfulImportAt = lastSuccessfulImportAt;
    return this;
  }

  /**
   * When the last import that finished wrote its last batch. Null when none has under the current configuration. This is the date the interface shows as \"last guide import\"; it is **not** the age of the provider's listings, and neither `EpgGrid.generated_at` nor the moment a client fetched the answer stands in for it. 
   * @return lastSuccessfulImportAt
   */
  @NotNull @Valid 
  @JsonProperty("last_successful_import_at")
  public OffsetDateTime getLastSuccessfulImportAt() {
    return lastSuccessfulImportAt;
  }

  public void setLastSuccessfulImportAt(OffsetDateTime lastSuccessfulImportAt) {
    this.lastSuccessfulImportAt = lastSuccessfulImportAt;
  }

  public EpgImportStatus lastAttemptStartedAt(OffsetDateTime lastAttemptStartedAt) {
    this.lastAttemptStartedAt = lastAttemptStartedAt;
    return this;
  }

  /**
   * When the latest import attempt began — the guide's own attempt, distinct from the catalogue synchronisation that contains it. Null when none is recorded. 
   * @return lastAttemptStartedAt
   */
  @NotNull @Valid 
  @JsonProperty("last_attempt_started_at")
  public OffsetDateTime getLastAttemptStartedAt() {
    return lastAttemptStartedAt;
  }

  public void setLastAttemptStartedAt(OffsetDateTime lastAttemptStartedAt) {
    this.lastAttemptStartedAt = lastAttemptStartedAt;
  }

  public EpgImportStatus lastAttemptFinishedAt(OffsetDateTime lastAttemptFinishedAt) {
    this.lastAttemptFinishedAt = lastAttemptFinishedAt;
    return this;
  }

  /**
   * When that attempt ended, whatever its outcome. Null while it is `RUNNING`, and when none is recorded. 
   * @return lastAttemptFinishedAt
   */
  @NotNull @Valid 
  @JsonProperty("last_attempt_finished_at")
  public OffsetDateTime getLastAttemptFinishedAt() {
    return lastAttemptFinishedAt;
  }

  public void setLastAttemptFinishedAt(OffsetDateTime lastAttemptFinishedAt) {
    this.lastAttemptFinishedAt = lastAttemptFinishedAt;
  }

  public EpgImportStatus lastAttemptStatus(EpgAttemptStatus lastAttemptStatus) {
    this.lastAttemptStatus = lastAttemptStatus;
    return this;
  }

  /**
   * Get lastAttemptStatus
   * @return lastAttemptStatus
   */
  @NotNull @Valid 
  @JsonProperty("last_attempt_status")
  public EpgAttemptStatus getLastAttemptStatus() {
    return lastAttemptStatus;
  }

  public void setLastAttemptStatus(EpgAttemptStatus lastAttemptStatus) {
    this.lastAttemptStatus = lastAttemptStatus;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EpgImportStatus epgImportStatus = (EpgImportStatus) o;
    return Objects.equals(this.configured, epgImportStatus.configured) &&
        Objects.equals(this.lastSuccessfulImportAt, epgImportStatus.lastSuccessfulImportAt) &&
        Objects.equals(this.lastAttemptStartedAt, epgImportStatus.lastAttemptStartedAt) &&
        Objects.equals(this.lastAttemptFinishedAt, epgImportStatus.lastAttemptFinishedAt) &&
        Objects.equals(this.lastAttemptStatus, epgImportStatus.lastAttemptStatus);
  }

  @Override
  public int hashCode() {
    return Objects.hash(configured, lastSuccessfulImportAt, lastAttemptStartedAt, lastAttemptFinishedAt, lastAttemptStatus);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EpgImportStatus {\n");
    sb.append("    configured: ").append(toIndentedString(configured)).append("\n");
    sb.append("    lastSuccessfulImportAt: ").append(toIndentedString(lastSuccessfulImportAt)).append("\n");
    sb.append("    lastAttemptStartedAt: ").append(toIndentedString(lastAttemptStartedAt)).append("\n");
    sb.append("    lastAttemptFinishedAt: ").append(toIndentedString(lastAttemptFinishedAt)).append("\n");
    sb.append("    lastAttemptStatus: ").append(toIndentedString(lastAttemptStatus)).append("\n");
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

