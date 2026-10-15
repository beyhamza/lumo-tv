package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.UUID;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.ProgressItemType;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * The item whose card is hidden — the same key as a save.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class HideProgressRequest {

  private UUID sourceId;

  private ProgressItemType itemType;

  private String itemRef;

  public HideProgressRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public HideProgressRequest(UUID sourceId, ProgressItemType itemType, String itemRef) {
    this.sourceId = sourceId;
    this.itemType = itemType;
    this.itemRef = itemRef;
  }

  public HideProgressRequest sourceId(UUID sourceId) {
    this.sourceId = sourceId;
    return this;
  }

  /**
   * Get sourceId
   * @return sourceId
   */
  @NotNull @Valid 
  @JsonProperty("source_id")
  public UUID getSourceId() {
    return sourceId;
  }

  public void setSourceId(UUID sourceId) {
    this.sourceId = sourceId;
  }

  public HideProgressRequest itemType(ProgressItemType itemType) {
    this.itemType = itemType;
    return this;
  }

  /**
   * Get itemType
   * @return itemType
   */
  @NotNull @Valid 
  @JsonProperty("item_type")
  public ProgressItemType getItemType() {
    return itemType;
  }

  public void setItemType(ProgressItemType itemType) {
    this.itemType = itemType;
  }

  public HideProgressRequest itemRef(String itemRef) {
    this.itemRef = itemRef;
    return this;
  }

  /**
   * Get itemRef
   * @return itemRef
   */
  @NotNull @Size(min = 1, max = 200) 
  @JsonProperty("item_ref")
  public String getItemRef() {
    return itemRef;
  }

  public void setItemRef(String itemRef) {
    this.itemRef = itemRef;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    HideProgressRequest hideProgressRequest = (HideProgressRequest) o;
    return Objects.equals(this.sourceId, hideProgressRequest.sourceId) &&
        Objects.equals(this.itemType, hideProgressRequest.itemType) &&
        Objects.equals(this.itemRef, hideProgressRequest.itemRef);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceId, itemType, itemRef);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class HideProgressRequest {\n");
    sb.append("    sourceId: ").append(toIndentedString(sourceId)).append("\n");
    sb.append("    itemType: ").append(toIndentedString(itemType)).append("\n");
    sb.append("    itemRef: ").append(toIndentedString(itemRef)).append("\n");
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

