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
 * Everything the player needs to open one film. Issued on demand, scoped to the owner.  A separate schema rather than a &#x60;channel_id&#x60; renamed to something generic: that rename would break three generated clients today to spare one duplicated object, and the two identifiers really do point at two different tables. The rules on &#x60;stream_url&#x60; are the ones written on [&#x60;PlaybackInfo&#x60;](#/components/schemas/PlaybackInfo) and are not repeated here — there is exactly one place they are stated, and it is that one.  **What a player must do differently with this URL.** It is a progressive file, not an HLS manifest: seeking works only if the user&#39;s server answers &#x60;Range&#x60; requests, and many do not. A player finds that out on the first attempt and says so, rather than drawing a scrubber that does nothing. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class VodPlaybackInfo {

  private UUID vodItemId;

  private String streamUrl;

  private @Nullable String userAgent = null;

  private @Nullable Integer maxConnections = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private @Nullable OffsetDateTime expiresAt = null;

  public VodPlaybackInfo() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public VodPlaybackInfo(UUID vodItemId, String streamUrl) {
    this.vodItemId = vodItemId;
    this.streamUrl = streamUrl;
  }

  public VodPlaybackInfo vodItemId(UUID vodItemId) {
    this.vodItemId = vodItemId;
    return this;
  }

  /**
   * Get vodItemId
   * @return vodItemId
   */
  @NotNull @Valid 
  @JsonProperty("vod_item_id")
  public UUID getVodItemId() {
    return vodItemId;
  }

  public void setVodItemId(UUID vodItemId) {
    this.vodItemId = vodItemId;
  }

  public VodPlaybackInfo streamUrl(String streamUrl) {
    this.streamUrl = streamUrl;
    return this;
  }

  /**
   * **Sensitive**, and typed `format: password` for the reason given on `PlaybackInfo.stream_url`: it must never reach a log line at any level, `DEBUG` included (AGENTS.md §5). 
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

  public VodPlaybackInfo userAgent(@Nullable String userAgent) {
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

  public VodPlaybackInfo maxConnections(@Nullable Integer maxConnections) {
    this.maxConnections = maxConnections;
    return this;
  }

  /**
   * Simultaneous streams the user's subscription allows. A film counts against that ceiling exactly as a channel does. 
   * @return maxConnections
   */
  
  @JsonProperty("max_connections")
  public @Nullable Integer getMaxConnections() {
    return maxConnections;
  }

  public void setMaxConnections(@Nullable Integer maxConnections) {
    this.maxConnections = maxConnections;
  }

  public VodPlaybackInfo expiresAt(@Nullable OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  /**
   * When this URL stops being valid, when the panel issues time-limited links. Null means no known expiry. 
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
    VodPlaybackInfo vodPlaybackInfo = (VodPlaybackInfo) o;
    return Objects.equals(this.vodItemId, vodPlaybackInfo.vodItemId) &&
        Objects.equals(this.streamUrl, vodPlaybackInfo.streamUrl) &&
        Objects.equals(this.userAgent, vodPlaybackInfo.userAgent) &&
        Objects.equals(this.maxConnections, vodPlaybackInfo.maxConnections) &&
        Objects.equals(this.expiresAt, vodPlaybackInfo.expiresAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(vodItemId, streamUrl, userAgent, maxConnections, expiresAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class VodPlaybackInfo {\n");
    sb.append("    vodItemId: ").append(toIndentedString(vodItemId)).append("\n");
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

