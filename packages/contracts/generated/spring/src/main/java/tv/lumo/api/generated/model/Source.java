package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.IngestionErrorCode;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.generated.model.SourceStatus;
import tv.lumo.api.generated.model.SyncStep;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * A registered source.  **This schema has no password property and never will.** The Xtream password is encrypted at rest (AES-256-GCM, data key wrapped by an out-of-database master key) and never leaves the &#x60;source&#x60; layer — not even for its owner (&#x60;docs/domain-model.md&#x60; §2). 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class Source {

  private UUID id;

  private String label;

  private SourceKind kind;

  private @Nullable String host = null;

  private @Nullable String username = null;

  private @Nullable String m3uUrl = null;

  private @Nullable String epgUrl = null;

  private SourceStatus status;

  private @Nullable SyncStep syncStep = null;

  private Boolean autoSync;

  private @Nullable IngestionErrorCode errorCode = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private @Nullable OffsetDateTime lastErrorAt = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private @Nullable OffsetDateTime lastSyncedAt = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private @Nullable OffsetDateTime expiresAt = null;

  private @Nullable Integer maxConnections = null;

  private @Nullable Integer channelCount = null;

  private @Nullable Integer categoryCount = null;

  public Source() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public Source(UUID id, String label, SourceKind kind, SourceStatus status, Boolean autoSync) {
    this.id = id;
    this.label = label;
    this.kind = kind;
    this.status = status;
    this.autoSync = autoSync;
  }

  public Source id(UUID id) {
    this.id = id;
    return this;
  }

  /**
   * Get id
   * @return id
   */
  @NotNull @Valid 
  @JsonProperty("id")
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Source label(String label) {
    this.label = label;
    return this;
  }

  /**
   * Name the user gave this source.
   * @return label
   */
  @NotNull 
  @JsonProperty("label")
  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public Source kind(SourceKind kind) {
    this.kind = kind;
    return this;
  }

  /**
   * Get kind
   * @return kind
   */
  @NotNull @Valid 
  @JsonProperty("kind")
  public SourceKind getKind() {
    return kind;
  }

  public void setKind(SourceKind kind) {
    this.kind = kind;
  }

  public Source host(@Nullable String host) {
    this.host = host;
    return this;
  }

  /**
   * Xtream base URL, normalised server-side. Returned so an edit form can keep it while the user corrects only what was wrong (US-06). 
   * @return host
   */
  
  @JsonProperty("host")
  public @Nullable String getHost() {
    return host;
  }

  public void setHost(@Nullable String host) {
    this.host = host;
  }

  public Source username(@Nullable String username) {
    this.username = username;
    return this;
  }

  /**
   * Xtream username. The matching password is never returned.
   * @return username
   */
  
  @JsonProperty("username")
  public @Nullable String getUsername() {
    return username;
  }

  public void setUsername(@Nullable String username) {
    this.username = username;
  }

  public Source m3uUrl(@Nullable String m3uUrl) {
    this.m3uUrl = m3uUrl;
    return this;
  }

  /**
   * Playlist URL, for `M3U_URL` sources.
   * @return m3uUrl
   */
  
  @JsonProperty("m3u_url")
  public @Nullable String getM3uUrl() {
    return m3uUrl;
  }

  public void setM3uUrl(@Nullable String m3uUrl) {
    this.m3uUrl = m3uUrl;
  }

  public Source epgUrl(@Nullable String epgUrl) {
    this.epgUrl = epgUrl;
    return this;
  }

  /**
   * Optional XMLTV guide URL, independent of the playlist.
   * @return epgUrl
   */
  
  @JsonProperty("epg_url")
  public @Nullable String getEpgUrl() {
    return epgUrl;
  }

  public void setEpgUrl(@Nullable String epgUrl) {
    this.epgUrl = epgUrl;
  }

  public Source status(SourceStatus status) {
    this.status = status;
    return this;
  }

  /**
   * Get status
   * @return status
   */
  @NotNull @Valid 
  @JsonProperty("status")
  public SourceStatus getStatus() {
    return status;
  }

  public void setStatus(SourceStatus status) {
    this.status = status;
  }

  public Source syncStep(@Nullable SyncStep syncStep) {
    this.syncStep = syncStep;
    return this;
  }

  /**
   * How far the running ingestion has got. Non-null only while `status` is `SYNCING`; cleared when it reaches `READY` or `ERROR`.  Rendered as a checklist while the user waits (mobile, écran 5). A client that ignores it falls back to an indeterminate progress bar, which is correct but worse. 
   * @return syncStep
   */
  @Valid 
  @JsonProperty("sync_step")
  public @Nullable SyncStep getSyncStep() {
    return syncStep;
  }

  public void setSyncStep(@Nullable SyncStep syncStep) {
    this.syncStep = syncStep;
  }

  public Source autoSync(Boolean autoSync) {
    this.autoSync = autoSync;
    return this;
  }

  /**
   * Whether the server re-synchronises this source on its own.  On the source rather than on the account or the device, and that is the whole point: re-synchronising is server work that hits the user's own IPTV server, so the decision belongs to the source it hits. One may want a playlist that moves refreshed nightly and a stable subscription left alone. On a device, the setting would have to be made three times and would still not describe what the server does while every device is asleep. 
   * @return autoSync
   */
  @NotNull 
  @JsonProperty("auto_sync")
  public Boolean getAutoSync() {
    return autoSync;
  }

  public void setAutoSync(Boolean autoSync) {
    this.autoSync = autoSync;
  }

  public Source errorCode(@Nullable IngestionErrorCode errorCode) {
    this.errorCode = errorCode;
    return this;
  }

  /**
   * Why the last ingestion failed. Non-null only when `status` is `ERROR`. A stable code, never a free-form message — the client owns the wording, in FR and EN. 
   * @return errorCode
   */
  @Valid 
  @JsonProperty("error_code")
  public @Nullable IngestionErrorCode getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(@Nullable IngestionErrorCode errorCode) {
    this.errorCode = errorCode;
  }

  public Source lastErrorAt(@Nullable OffsetDateTime lastErrorAt) {
    this.lastErrorAt = lastErrorAt;
    return this;
  }

  /**
   * When the ingestion that set `error_code` failed. Non-null only when `status` is `ERROR`, and cleared by the next success.  Separate from `last_synced_at` because the two answer different questions and a single timestamp cannot answer both: one row of the account screen reads \"1 248 channels · checked 2 h ago\", the row below it reads \"credentials refused **since yesterday**\".  The age is what makes the message actionable. \"Credentials refused\" alone does not say whether the user missed two hours of television or two weeks. 
   * @return lastErrorAt
   */
  @Valid 
  @JsonProperty("last_error_at")
  public @Nullable OffsetDateTime getLastErrorAt() {
    return lastErrorAt;
  }

  public void setLastErrorAt(@Nullable OffsetDateTime lastErrorAt) {
    this.lastErrorAt = lastErrorAt;
  }

  public Source lastSyncedAt(@Nullable OffsetDateTime lastSyncedAt) {
    this.lastSyncedAt = lastSyncedAt;
    return this;
  }

  /**
   * Last ingestion that **succeeded**. Unchanged by a failed attempt — see `last_error_at`. 
   * @return lastSyncedAt
   */
  @Valid 
  @JsonProperty("last_synced_at")
  public @Nullable OffsetDateTime getLastSyncedAt() {
    return lastSyncedAt;
  }

  public void setLastSyncedAt(@Nullable OffsetDateTime lastSyncedAt) {
    this.lastSyncedAt = lastSyncedAt;
  }

  public Source expiresAt(@Nullable OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  /**
   * Expiry of the user's Xtream account, as reported by the panel. Shown after a successful registration (US-06). Null for M3U sources. 
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

  public Source maxConnections(@Nullable Integer maxConnections) {
    this.maxConnections = maxConnections;
    return this;
  }

  /**
   * Simultaneous streams the user's subscription allows, as reported by the panel. Null when unknown or not applicable. 
   * @return maxConnections
   */
  
  @JsonProperty("max_connections")
  public @Nullable Integer getMaxConnections() {
    return maxConnections;
  }

  public void setMaxConnections(@Nullable Integer maxConnections) {
    this.maxConnections = maxConnections;
  }

  public Source channelCount(@Nullable Integer channelCount) {
    this.channelCount = channelCount;
    return this;
  }

  /**
   * Channels ingested from this source. Derived, not stored on the entity. Null until the first successful ingestion; it is what \"we found N channels\" is rendered from (US-06, US-07). 
   * @return channelCount
   */
  
  @JsonProperty("channel_count")
  public @Nullable Integer getChannelCount() {
    return channelCount;
  }

  public void setChannelCount(@Nullable Integer channelCount) {
    this.channelCount = channelCount;
  }

  public Source categoryCount(@Nullable Integer categoryCount) {
    this.categoryCount = categoryCount;
    return this;
  }

  /**
   * Categories ingested from this source. Derived and nullable on exactly the same terms as `channel_count`, with which it is displayed side by side on the success screen — \"1 248 chaînes · 96 catégories\". Half of that line was available; this is the other half. 
   * @return categoryCount
   */
  
  @JsonProperty("category_count")
  public @Nullable Integer getCategoryCount() {
    return categoryCount;
  }

  public void setCategoryCount(@Nullable Integer categoryCount) {
    this.categoryCount = categoryCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Source source = (Source) o;
    return Objects.equals(this.id, source.id) &&
        Objects.equals(this.label, source.label) &&
        Objects.equals(this.kind, source.kind) &&
        Objects.equals(this.host, source.host) &&
        Objects.equals(this.username, source.username) &&
        Objects.equals(this.m3uUrl, source.m3uUrl) &&
        Objects.equals(this.epgUrl, source.epgUrl) &&
        Objects.equals(this.status, source.status) &&
        Objects.equals(this.syncStep, source.syncStep) &&
        Objects.equals(this.autoSync, source.autoSync) &&
        Objects.equals(this.errorCode, source.errorCode) &&
        Objects.equals(this.lastErrorAt, source.lastErrorAt) &&
        Objects.equals(this.lastSyncedAt, source.lastSyncedAt) &&
        Objects.equals(this.expiresAt, source.expiresAt) &&
        Objects.equals(this.maxConnections, source.maxConnections) &&
        Objects.equals(this.channelCount, source.channelCount) &&
        Objects.equals(this.categoryCount, source.categoryCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, label, kind, host, username, m3uUrl, epgUrl, status, syncStep, autoSync, errorCode, lastErrorAt, lastSyncedAt, expiresAt, maxConnections, channelCount, categoryCount);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Source {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    label: ").append(toIndentedString(label)).append("\n");
    sb.append("    kind: ").append(toIndentedString(kind)).append("\n");
    sb.append("    host: ").append(toIndentedString(host)).append("\n");
    sb.append("    username: ").append(toIndentedString(username)).append("\n");
    sb.append("    m3uUrl: ").append(toIndentedString(m3uUrl)).append("\n");
    sb.append("    epgUrl: ").append(toIndentedString(epgUrl)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    syncStep: ").append(toIndentedString(syncStep)).append("\n");
    sb.append("    autoSync: ").append(toIndentedString(autoSync)).append("\n");
    sb.append("    errorCode: ").append(toIndentedString(errorCode)).append("\n");
    sb.append("    lastErrorAt: ").append(toIndentedString(lastErrorAt)).append("\n");
    sb.append("    lastSyncedAt: ").append(toIndentedString(lastSyncedAt)).append("\n");
    sb.append("    expiresAt: ").append(toIndentedString(expiresAt)).append("\n");
    sb.append("    maxConnections: ").append(toIndentedString(maxConnections)).append("\n");
    sb.append("    channelCount: ").append(toIndentedString(channelCount)).append("\n");
    sb.append("    categoryCount: ").append(toIndentedString(categoryCount)).append("\n");
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

