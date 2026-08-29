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
 * Everything the player needs to open one episode. Issued on demand, scoped to the owner.  A third schema rather than a generic one with a renamed identifier: the three point at three different tables, and a player handed \&quot;an id\&quot; it cannot name is how an episode gets looked up among the films. The rules on &#x60;stream_url&#x60; are the ones written on &#x60;PlaybackInfo&#x60; and are not repeated — there is exactly one place they are stated.  **An episode plays like a film**: a progressive file, not an HLS manifest, so seeking works only if the user&#39;s server answers &#x60;Range&#x60; requests. A player finds that out on the first attempt and says so. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class EpisodePlaybackInfo {

  private UUID episodeId;

  private String streamUrl;

  private @Nullable String userAgent = null;

  private @Nullable Integer maxConnections = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private @Nullable OffsetDateTime expiresAt = null;

  public EpisodePlaybackInfo() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public EpisodePlaybackInfo(UUID episodeId, String streamUrl) {
    this.episodeId = episodeId;
    this.streamUrl = streamUrl;
  }

  public EpisodePlaybackInfo episodeId(UUID episodeId) {
    this.episodeId = episodeId;
    return this;
  }

  /**
   * Get episodeId
   * @return episodeId
   */
  @NotNull @Valid 
  @JsonProperty("episode_id")
  public UUID getEpisodeId() {
    return episodeId;
  }

  public void setEpisodeId(UUID episodeId) {
    this.episodeId = episodeId;
  }

  public EpisodePlaybackInfo streamUrl(String streamUrl) {
    this.streamUrl = streamUrl;
    return this;
  }

  /**
   * **Sensitive**, and typed `format: password` for the reason given on `PlaybackInfo.stream_url`: it must never reach a log line at any level, `DEBUG` included. 
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

  public EpisodePlaybackInfo userAgent(@Nullable String userAgent) {
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

  public EpisodePlaybackInfo maxConnections(@Nullable Integer maxConnections) {
    this.maxConnections = maxConnections;
    return this;
  }

  /**
   * Simultaneous streams the user's subscription allows. An episode counts against that ceiling exactly as a channel or a film does. 
   * @return maxConnections
   */
  
  @JsonProperty("max_connections")
  public @Nullable Integer getMaxConnections() {
    return maxConnections;
  }

  public void setMaxConnections(@Nullable Integer maxConnections) {
    this.maxConnections = maxConnections;
  }

  public EpisodePlaybackInfo expiresAt(@Nullable OffsetDateTime expiresAt) {
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
    EpisodePlaybackInfo episodePlaybackInfo = (EpisodePlaybackInfo) o;
    return Objects.equals(this.episodeId, episodePlaybackInfo.episodeId) &&
        Objects.equals(this.streamUrl, episodePlaybackInfo.streamUrl) &&
        Objects.equals(this.userAgent, episodePlaybackInfo.userAgent) &&
        Objects.equals(this.maxConnections, episodePlaybackInfo.maxConnections) &&
        Objects.equals(this.expiresAt, episodePlaybackInfo.expiresAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(episodeId, streamUrl, userAgent, maxConnections, expiresAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EpisodePlaybackInfo {\n");
    sb.append("    episodeId: ").append(toIndentedString(episodeId)).append("\n");
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

