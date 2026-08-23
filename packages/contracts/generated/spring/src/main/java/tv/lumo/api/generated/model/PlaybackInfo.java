package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Everything the player needs to open one channel. Issued on demand, scoped to the owner. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class PlaybackInfo {

  private UUID channelId;

  private String streamUrl;

  private @Nullable String userAgent = null;

  private @Nullable Integer maxConnections = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private @Nullable OffsetDateTime expiresAt = null;

  public PlaybackInfo() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public PlaybackInfo(UUID channelId, String streamUrl) {
    this.channelId = channelId;
    this.streamUrl = streamUrl;
  }

  public PlaybackInfo channelId(UUID channelId) {
    this.channelId = channelId;
    return this;
  }

  /**
   * Get channelId
   * @return channelId
   */
  @NotNull @Valid 
  @JsonProperty("channel_id")
  public UUID getChannelId() {
    return channelId;
  }

  public void setChannelId(UUID channelId) {
    this.channelId = channelId;
  }

  public PlaybackInfo streamUrl(String streamUrl) {
    this.streamUrl = streamUrl;
    return this;
  }

  /**
   * **Sensitive.** Typed `format: password` rather than `format: uri` on purpose: both map to a plain string, but `password` makes every generator mask this property in `toString()`. A stream URL must never reach a log line at any level, `DEBUG` included (AGENTS.md §5), and the contract is the cheapest place to guarantee that.  Direct URL to the user's IPTV server, embedding their credentials in most Xtream panels.  Never logged, never persisted client-side beyond the playback session, never shared between accounts. The player opens it directly; it does not transit through Lumo. 
   * @return streamUrl
   */
  @NotNull 
  @JsonProperty("stream_url")
  public String getStreamUrl() {
    return streamUrl;
  }

  public void setStreamUrl(String streamUrl) {
    this.streamUrl = streamUrl;
  }

  public PlaybackInfo userAgent(@Nullable String userAgent) {
    this.userAgent = userAgent;
    return this;
  }

  /**
   * User-Agent the player should send, when the source requires a specific one. Null means the client's default. 
   * @return userAgent
   */
  
  @JsonProperty("user_agent")
  public @Nullable String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(@Nullable String userAgent) {
    this.userAgent = userAgent;
  }

  public PlaybackInfo maxConnections(@Nullable Integer maxConnections) {
    this.maxConnections = maxConnections;
    return this;
  }

  /**
   * Simultaneous streams the user's subscription allows, echoed from the source so the player can explain a rejected stream (US-09). 
   * @return maxConnections
   */
  
  @JsonProperty("max_connections")
  public @Nullable Integer getMaxConnections() {
    return maxConnections;
  }

  public void setMaxConnections(@Nullable Integer maxConnections) {
    this.maxConnections = maxConnections;
  }

  public PlaybackInfo expiresAt(@Nullable OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  /**
   * When this URL stops being valid, when the panel issues time-limited links. Null means no known expiry; the client re-requests on failure rather than assuming. 
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
    PlaybackInfo playbackInfo = (PlaybackInfo) o;
    return Objects.equals(this.channelId, playbackInfo.channelId) &&
        Objects.equals(this.streamUrl, playbackInfo.streamUrl) &&
        Objects.equals(this.userAgent, playbackInfo.userAgent) &&
        Objects.equals(this.maxConnections, playbackInfo.maxConnections) &&
        Objects.equals(this.expiresAt, playbackInfo.expiresAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(channelId, streamUrl, userAgent, maxConnections, expiresAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PlaybackInfo {\n");
    sb.append("    channelId: ").append(toIndentedString(channelId)).append("\n");
    sb.append("    streamUrl: ").append("*").append("\n");
    sb.append("    userAgent: ").append(toIndentedString(userAgent)).append("\n");
    sb.append("    maxConnections: ").append(toIndentedString(maxConnections)).append("\n");
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

