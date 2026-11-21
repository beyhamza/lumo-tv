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
import tv.lumo.api.generated.model.LegalDocumentType;
import tv.lumo.api.generated.model.LegalVersionStatus;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * One version of a legal document. Bodies are Markdown, limited to what the site renders: &#x60;##&#x60; and &#x60;###&#x60; headings, &#x60;-&#x60; lists, &#x60;**bold**&#x60; and &#x60;[links](https://…)&#x60;. HTML is shown as text. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class LegalDocumentVersion {

  private UUID id;

  private LegalDocumentType documentType;

  private LegalVersionStatus status;

  private @Nullable Integer version = null;

  private String titleFr;

  private String titleEn;

  private String bodyFr;

  private String bodyEn;

  private @Nullable String changeSummary = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private @Nullable OffsetDateTime publishedAt = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime createdAt;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime updatedAt;

  public LegalDocumentVersion() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public LegalDocumentVersion(UUID id, LegalDocumentType documentType, LegalVersionStatus status, String titleFr, String titleEn, String bodyFr, String bodyEn, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    this.id = id;
    this.documentType = documentType;
    this.status = status;
    this.titleFr = titleFr;
    this.titleEn = titleEn;
    this.bodyFr = bodyFr;
    this.bodyEn = bodyEn;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public LegalDocumentVersion id(UUID id) {
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

  public LegalDocumentVersion documentType(LegalDocumentType documentType) {
    this.documentType = documentType;
    return this;
  }

  /**
   * Get documentType
   * @return documentType
   */
  @NotNull @Valid 
  @JsonProperty("document_type")
  public LegalDocumentType getDocumentType() {
    return documentType;
  }

  public void setDocumentType(LegalDocumentType documentType) {
    this.documentType = documentType;
  }

  public LegalDocumentVersion status(LegalVersionStatus status) {
    this.status = status;
    return this;
  }

  /**
   * Get status
   * @return status
   */
  @NotNull @Valid 
  @JsonProperty("status")
  public LegalVersionStatus getStatus() {
    return status;
  }

  public void setStatus(LegalVersionStatus status) {
    this.status = status;
  }

  public LegalDocumentVersion version(@Nullable Integer version) {
    this.version = version;
    return this;
  }

  /**
   * 1, 2, 3… within the document. Null until published.
   * @return version
   */
  
  @JsonProperty("version")
  public @Nullable Integer getVersion() {
    return version;
  }

  public void setVersion(@Nullable Integer version) {
    this.version = version;
  }

  public LegalDocumentVersion titleFr(String titleFr) {
    this.titleFr = titleFr;
    return this;
  }

  /**
   * Get titleFr
   * @return titleFr
   */
  @NotNull 
  @JsonProperty("title_fr")
  public String getTitleFr() {
    return titleFr;
  }

  public void setTitleFr(String titleFr) {
    this.titleFr = titleFr;
  }

  public LegalDocumentVersion titleEn(String titleEn) {
    this.titleEn = titleEn;
    return this;
  }

  /**
   * Get titleEn
   * @return titleEn
   */
  @NotNull 
  @JsonProperty("title_en")
  public String getTitleEn() {
    return titleEn;
  }

  public void setTitleEn(String titleEn) {
    this.titleEn = titleEn;
  }

  public LegalDocumentVersion bodyFr(String bodyFr) {
    this.bodyFr = bodyFr;
    return this;
  }

  /**
   * Get bodyFr
   * @return bodyFr
   */
  @NotNull 
  @JsonProperty("body_fr")
  public String getBodyFr() {
    return bodyFr;
  }

  public void setBodyFr(String bodyFr) {
    this.bodyFr = bodyFr;
  }

  public LegalDocumentVersion bodyEn(String bodyEn) {
    this.bodyEn = bodyEn;
    return this;
  }

  /**
   * Get bodyEn
   * @return bodyEn
   */
  @NotNull 
  @JsonProperty("body_en")
  public String getBodyEn() {
    return bodyEn;
  }

  public void setBodyEn(String bodyEn) {
    this.bodyEn = bodyEn;
  }

  public LegalDocumentVersion changeSummary(@Nullable String changeSummary) {
    this.changeSummary = changeSummary;
    return this;
  }

  /**
   * What changed from the previous version.
   * @return changeSummary
   */
  
  @JsonProperty("change_summary")
  public @Nullable String getChangeSummary() {
    return changeSummary;
  }

  public void setChangeSummary(@Nullable String changeSummary) {
    this.changeSummary = changeSummary;
  }

  public LegalDocumentVersion publishedAt(@Nullable OffsetDateTime publishedAt) {
    this.publishedAt = publishedAt;
    return this;
  }

  /**
   * Get publishedAt
   * @return publishedAt
   */
  @Valid 
  @JsonProperty("published_at")
  public @Nullable OffsetDateTime getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(@Nullable OffsetDateTime publishedAt) {
    this.publishedAt = publishedAt;
  }

  public LegalDocumentVersion createdAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /**
   * Get createdAt
   * @return createdAt
   */
  @NotNull @Valid 
  @JsonProperty("created_at")
  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LegalDocumentVersion updatedAt(OffsetDateTime updatedAt) {
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
    LegalDocumentVersion legalDocumentVersion = (LegalDocumentVersion) o;
    return Objects.equals(this.id, legalDocumentVersion.id) &&
        Objects.equals(this.documentType, legalDocumentVersion.documentType) &&
        Objects.equals(this.status, legalDocumentVersion.status) &&
        Objects.equals(this.version, legalDocumentVersion.version) &&
        Objects.equals(this.titleFr, legalDocumentVersion.titleFr) &&
        Objects.equals(this.titleEn, legalDocumentVersion.titleEn) &&
        Objects.equals(this.bodyFr, legalDocumentVersion.bodyFr) &&
        Objects.equals(this.bodyEn, legalDocumentVersion.bodyEn) &&
        Objects.equals(this.changeSummary, legalDocumentVersion.changeSummary) &&
        Objects.equals(this.publishedAt, legalDocumentVersion.publishedAt) &&
        Objects.equals(this.createdAt, legalDocumentVersion.createdAt) &&
        Objects.equals(this.updatedAt, legalDocumentVersion.updatedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, documentType, status, version, titleFr, titleEn, bodyFr, bodyEn, changeSummary, publishedAt, createdAt, updatedAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class LegalDocumentVersion {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    documentType: ").append(toIndentedString(documentType)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    version: ").append(toIndentedString(version)).append("\n");
    sb.append("    titleFr: ").append(toIndentedString(titleFr)).append("\n");
    sb.append("    titleEn: ").append(toIndentedString(titleEn)).append("\n");
    sb.append("    bodyFr: ").append(toIndentedString(bodyFr)).append("\n");
    sb.append("    bodyEn: ").append(toIndentedString(bodyEn)).append("\n");
    sb.append("    changeSummary: ").append(toIndentedString(changeSummary)).append("\n");
    sb.append("    publishedAt: ").append(toIndentedString(publishedAt)).append("\n");
    sb.append("    createdAt: ").append(toIndentedString(createdAt)).append("\n");
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

