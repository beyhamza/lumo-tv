package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.ProgressItemType;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Upsert keyed on &#x60;(item_type, item_ref)&#x60; for the caller.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class SaveProgressRequest {

  private ProgressItemType itemType;

  private String itemRef;

  private Long positionMs;

  private @Nullable Long durationMs = null;

  public SaveProgressRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public SaveProgressRequest(ProgressItemType itemType, String itemRef, Long positionMs) {
    this.itemType = itemType;
    this.itemRef = itemRef;
    this.positionMs = positionMs;
  }

  public SaveProgressRequest itemType(ProgressItemType itemType) {
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

  public SaveProgressRequest itemRef(String itemRef) {
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

  public SaveProgressRequest positionMs(Long positionMs) {
    this.positionMs = positionMs;
    return this;
  }

  /**
   * Get positionMs
   * minimum: 0
   * @return positionMs
   */
  @NotNull @Min(0L) 
  @JsonProperty("position_ms")
  public Long getPositionMs() {
    return positionMs;
  }

  public void setPositionMs(Long positionMs) {
    this.positionMs = positionMs;
  }

  public SaveProgressRequest durationMs(@Nullable Long durationMs) {
    this.durationMs = durationMs;
    return this;
  }

  /**
   * Get durationMs
   * minimum: 0
   * @return durationMs
   */
  @Min(0L) 
  @JsonProperty("duration_ms")
  public @Nullable Long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(@Nullable Long durationMs) {
    this.durationMs = durationMs;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SaveProgressRequest saveProgressRequest = (SaveProgressRequest) o;
    return Objects.equals(this.itemType, saveProgressRequest.itemType) &&
        Objects.equals(this.itemRef, saveProgressRequest.itemRef) &&
        Objects.equals(this.positionMs, saveProgressRequest.positionMs) &&
        Objects.equals(this.durationMs, saveProgressRequest.durationMs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(itemType, itemRef, positionMs, durationMs);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SaveProgressRequest {\n");
    sb.append("    itemType: ").append(toIndentedString(itemType)).append("\n");
    sb.append("    itemRef: ").append(toIndentedString(itemRef)).append("\n");
    sb.append("    positionMs: ").append(toIndentedString(positionMs)).append("\n");
    sb.append("    durationMs: ").append(toIndentedString(durationMs)).append("\n");
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

