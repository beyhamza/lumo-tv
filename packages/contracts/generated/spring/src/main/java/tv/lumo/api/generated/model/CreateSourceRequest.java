package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.SourceKind;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * &#x60;kind&#x60; decides which properties are required:  | &#x60;kind&#x60; | Required | Optional | |---|---|---| | &#x60;XTREAM&#x60; | &#x60;label&#x60;, &#x60;host&#x60;, &#x60;username&#x60;, &#x60;password&#x60; | &#x60;epg_url&#x60; | | &#x60;M3U_URL&#x60; | &#x60;label&#x60;, &#x60;m3u_url&#x60; | &#x60;epg_url&#x60; |  &#x60;M3U_FILE&#x60; is not creatable through this operation.  The combination is validated server-side; a mismatch is &#x60;400&#x60; &#x60;VALIDATION_FAILED&#x60; with per-field &#x60;errors&#x60;. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class CreateSourceRequest {

  private String label;

  private SourceKind kind;

  private @Nullable String host = null;

  private @Nullable String username = null;

  private @Nullable String password = null;

  private @Nullable String m3uUrl = null;

  private @Nullable String epgUrl = null;

  private Boolean autoSync = true;

  public CreateSourceRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public CreateSourceRequest(String label, SourceKind kind) {
    this.label = label;
    this.kind = kind;
  }

  public CreateSourceRequest label(String label) {
    this.label = label;
    return this;
  }

  /**
   * Get label
   * @return label
   */
  @NotNull @Size(min = 1, max = 100) 
  @JsonProperty("label")
  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public CreateSourceRequest kind(SourceKind kind) {
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

  public CreateSourceRequest host(@Nullable String host) {
    this.host = host;
    return this;
  }

  /**
   * Xtream base URL. Accepted with or without a scheme, with or without a port, with or without a trailing slash — normalised rather than rejected (US-06). 
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

  public CreateSourceRequest username(@Nullable String username) {
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

  public CreateSourceRequest password(@Nullable String password) {
    this.password = password;
    return this;
  }

  /**
   * Xtream password. **Write-only, by contract.** Encrypted with AES-256-GCM before persistence, never returned by any operation, never written to a log at any level including `DEBUG`. 
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

  public CreateSourceRequest m3uUrl(@Nullable String m3uUrl) {
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

  public CreateSourceRequest epgUrl(@Nullable String epgUrl) {
    this.epgUrl = epgUrl;
    return this;
  }

  /**
   * Optional XMLTV URL, a separate field from the playlist URL.
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

  public CreateSourceRequest autoSync(Boolean autoSync) {
    this.autoSync = autoSync;
    return this;
  }

  /**
   * Whether the server re-synchronises this source on its own. Defaults to true: a catalogue that silently goes stale is the failure the user cannot diagnose. 
   * @return autoSync
   */
  
  @JsonProperty("auto_sync")
  public Boolean getAutoSync() {
    return autoSync;
  }

  public void setAutoSync(Boolean autoSync) {
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
    CreateSourceRequest createSourceRequest = (CreateSourceRequest) o;
    return Objects.equals(this.label, createSourceRequest.label) &&
        Objects.equals(this.kind, createSourceRequest.kind) &&
        Objects.equals(this.host, createSourceRequest.host) &&
        Objects.equals(this.username, createSourceRequest.username) &&
        Objects.equals(this.password, createSourceRequest.password) &&
        Objects.equals(this.m3uUrl, createSourceRequest.m3uUrl) &&
        Objects.equals(this.epgUrl, createSourceRequest.epgUrl) &&
        Objects.equals(this.autoSync, createSourceRequest.autoSync);
  }

  @Override
  public int hashCode() {
    return Objects.hash(label, kind, host, username, password, m3uUrl, epgUrl, autoSync);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CreateSourceRequest {\n");
    sb.append("    label: ").append(toIndentedString(label)).append("\n");
    sb.append("    kind: ").append(toIndentedString(kind)).append("\n");
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

