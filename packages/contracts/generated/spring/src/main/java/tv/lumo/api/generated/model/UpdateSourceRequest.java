package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Omitted properties are left unchanged. Changing &#x60;host&#x60;, &#x60;username&#x60;, &#x60;password&#x60;, &#x60;m3u_url&#x60; or &#x60;epg_url&#x60; re-triggers ingestion. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class UpdateSourceRequest {

  private @Nullable String label;

  private @Nullable String host = null;

  private @Nullable String username = null;

  private @Nullable String password = null;

  private @Nullable String m3uUrl = null;

  private @Nullable String epgUrl = null;

  private @Nullable Boolean autoSync;

  public UpdateSourceRequest label(@Nullable String label) {
    this.label = label;
    return this;
  }

  /**
   * Get label
   * @return label
   */
  @Size(min = 1, max = 100) 
  @JsonProperty("label")
  public @Nullable String getLabel() {
    return label;
  }

  public void setLabel(@Nullable String label) {
    this.label = label;
  }

  public UpdateSourceRequest host(@Nullable String host) {
    this.host = host;
    return this;
  }

  /**
   * Get host
   * @return host
   */
  @Size(max = 500) 
  @JsonProperty("host")
  public @Nullable String getHost() {
    return host;
  }

  public void setHost(@Nullable String host) {
    this.host = host;
  }

  public UpdateSourceRequest username(@Nullable String username) {
    this.username = username;
    return this;
  }

  /**
   * Get username
   * @return username
   */
  @Size(max = 200) 
  @JsonProperty("username")
  public @Nullable String getUsername() {
    return username;
  }

  public void setUsername(@Nullable String username) {
    this.username = username;
  }

  public UpdateSourceRequest password(@Nullable String password) {
    this.password = password;
    return this;
  }

  /**
   * Write-only. Re-encrypted on write, never returned.
   * @return password
   */
  @Size(max = 200) 
  @JsonProperty("password")
  public @Nullable String getPassword() {
    return password;
  }

  public void setPassword(@Nullable String password) {
    this.password = password;
  }

  public UpdateSourceRequest m3uUrl(@Nullable String m3uUrl) {
    this.m3uUrl = m3uUrl;
    return this;
  }

  /**
   * Get m3uUrl
   * @return m3uUrl
   */
  @Size(max = 2000) 
  @JsonProperty("m3u_url")
  public @Nullable String getM3uUrl() {
    return m3uUrl;
  }

  public void setM3uUrl(@Nullable String m3uUrl) {
    this.m3uUrl = m3uUrl;
  }

  public UpdateSourceRequest epgUrl(@Nullable String epgUrl) {
    this.epgUrl = epgUrl;
    return this;
  }

  /**
   * Get epgUrl
   * @return epgUrl
   */
  @Size(max = 2000) 
  @JsonProperty("epg_url")
  public @Nullable String getEpgUrl() {
    return epgUrl;
  }

  public void setEpgUrl(@Nullable String epgUrl) {
    this.epgUrl = epgUrl;
  }

  public UpdateSourceRequest autoSync(@Nullable Boolean autoSync) {
    this.autoSync = autoSync;
    return this;
  }

  /**
   * Toggling this does **not** re-trigger an ingestion: it only decides whether the server will start one by itself later. It is the one property in this request that leaves the catalogue alone. 
   * @return autoSync
   */
  
  @JsonProperty("auto_sync")
  public @Nullable Boolean getAutoSync() {
    return autoSync;
  }

  public void setAutoSync(@Nullable Boolean autoSync) {
    this.autoSync = autoSync;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UpdateSourceRequest updateSourceRequest = (UpdateSourceRequest) o;
    return Objects.equals(this.label, updateSourceRequest.label) &&
        Objects.equals(this.host, updateSourceRequest.host) &&
        Objects.equals(this.username, updateSourceRequest.username) &&
        Objects.equals(this.password, updateSourceRequest.password) &&
        Objects.equals(this.m3uUrl, updateSourceRequest.m3uUrl) &&
        Objects.equals(this.epgUrl, updateSourceRequest.epgUrl) &&
        Objects.equals(this.autoSync, updateSourceRequest.autoSync);
  }

  @Override
  public int hashCode() {
    return Objects.hash(label, host, username, password, m3uUrl, epgUrl, autoSync);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class UpdateSourceRequest {\n");
    sb.append("    label: ").append(toIndentedString(label)).append("\n");
    sb.append("    host: ").append(toIndentedString(host)).append("\n");
    sb.append("    username: ").append(toIndentedString(username)).append("\n");
    sb.append("    password: ").append("*").append("\n");
    sb.append("    m3uUrl: ").append(toIndentedString(m3uUrl)).append("\n");
    sb.append("    epgUrl: ").append(toIndentedString(epgUrl)).append("\n");
    sb.append("    autoSync: ").append(toIndentedString(autoSync)).append("\n");
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

