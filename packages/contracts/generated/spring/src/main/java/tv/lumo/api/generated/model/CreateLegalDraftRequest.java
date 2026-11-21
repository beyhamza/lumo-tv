package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.LegalDocumentType;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * The first content of a new draft. Texts may be empty until publishing.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class CreateLegalDraftRequest {

  private LegalDocumentType documentType;

  private String titleFr;

  private String titleEn;

  private String bodyFr;

  private String bodyEn;

  private @Nullable String changeSummary = null;

  public CreateLegalDraftRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public CreateLegalDraftRequest(LegalDocumentType documentType, String titleFr, String titleEn, String bodyFr, String bodyEn) {
    this.documentType = documentType;
    this.titleFr = titleFr;
    this.titleEn = titleEn;
    this.bodyFr = bodyFr;
    this.bodyEn = bodyEn;
  }

  public CreateLegalDraftRequest documentType(LegalDocumentType documentType) {
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

  public CreateLegalDraftRequest titleFr(String titleFr) {
    this.titleFr = titleFr;
    return this;
  }

  /**
   * Get titleFr
   * @return titleFr
   */
  @NotNull @Size(max = 200) 
  @JsonProperty("title_fr")
  public String getTitleFr() {
    return titleFr;
  }

  public void setTitleFr(String titleFr) {
    this.titleFr = titleFr;
  }

  public CreateLegalDraftRequest titleEn(String titleEn) {
    this.titleEn = titleEn;
    return this;
  }

  /**
   * Get titleEn
   * @return titleEn
   */
  @NotNull @Size(max = 200) 
  @JsonProperty("title_en")
  public String getTitleEn() {
    return titleEn;
  }

  public void setTitleEn(String titleEn) {
    this.titleEn = titleEn;
  }

  public CreateLegalDraftRequest bodyFr(String bodyFr) {
    this.bodyFr = bodyFr;
    return this;
  }

  /**
   * Get bodyFr
   * @return bodyFr
   */
  @NotNull @Size(max = 200000) 
  @JsonProperty("body_fr")
  public String getBodyFr() {
    return bodyFr;
  }

  public void setBodyFr(String bodyFr) {
    this.bodyFr = bodyFr;
  }

  public CreateLegalDraftRequest bodyEn(String bodyEn) {
    this.bodyEn = bodyEn;
    return this;
  }

  /**
   * Get bodyEn
   * @return bodyEn
   */
  @NotNull @Size(max = 200000) 
  @JsonProperty("body_en")
  public String getBodyEn() {
    return bodyEn;
  }

  public void setBodyEn(String bodyEn) {
    this.bodyEn = bodyEn;
  }

  public CreateLegalDraftRequest changeSummary(@Nullable String changeSummary) {
    this.changeSummary = changeSummary;
    return this;
  }

  /**
   * Get changeSummary
   * @return changeSummary
   */
  @Size(max = 500) 
  @JsonProperty("change_summary")
  public @Nullable String getChangeSummary() {
    return changeSummary;
  }

  public void setChangeSummary(@Nullable String changeSummary) {
    this.changeSummary = changeSummary;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CreateLegalDraftRequest createLegalDraftRequest = (CreateLegalDraftRequest) o;
    return Objects.equals(this.documentType, createLegalDraftRequest.documentType) &&
        Objects.equals(this.titleFr, createLegalDraftRequest.titleFr) &&
        Objects.equals(this.titleEn, createLegalDraftRequest.titleEn) &&
        Objects.equals(this.bodyFr, createLegalDraftRequest.bodyFr) &&
        Objects.equals(this.bodyEn, createLegalDraftRequest.bodyEn) &&
        Objects.equals(this.changeSummary, createLegalDraftRequest.changeSummary);
  }

  @Override
  public int hashCode() {
    return Objects.hash(documentType, titleFr, titleEn, bodyFr, bodyEn, changeSummary);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CreateLegalDraftRequest {\n");
    sb.append("    documentType: ").append(toIndentedString(documentType)).append("\n");
    sb.append("    titleFr: ").append(toIndentedString(titleFr)).append("\n");
    sb.append("    titleEn: ").append(toIndentedString(titleEn)).append("\n");
    sb.append("    bodyFr: ").append(toIndentedString(bodyFr)).append("\n");
    sb.append("    bodyEn: ").append(toIndentedString(bodyEn)).append("\n");
    sb.append("    changeSummary: ").append(toIndentedString(changeSummary)).append("\n");
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

