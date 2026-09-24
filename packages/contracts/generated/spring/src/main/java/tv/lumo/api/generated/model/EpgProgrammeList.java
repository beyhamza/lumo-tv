package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.EpgImportStatus;
import tv.lumo.api.generated.model.EpgProgramme;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * The programmes of one channel over a window.  &#x60;epg&#x60; is the import status of the channel&#39;s source, so a day view can say the same thing about freshness as the grid does. It is additive and optional in the schema: a client generated before it existed ignores it and loses nothing it had. The server always sends it. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class EpgProgrammeList {

  @Valid
  private List<@Valid EpgProgramme> items = new ArrayList<>();

  private @Nullable EpgImportStatus epg;

  public EpgProgrammeList() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public EpgProgrammeList(List<@Valid EpgProgramme> items) {
    this.items = items;
  }

  public EpgProgrammeList items(List<@Valid EpgProgramme> items) {
    this.items = items;
    return this;
  }

  public EpgProgrammeList addItemsItem(EpgProgramme itemsItem) {
    if (this.items == null) {
      this.items = new ArrayList<>();
    }
    this.items.add(itemsItem);
    return this;
  }

  /**
   * Get items
   * @return items
   */
  @NotNull @Valid 
  @JsonProperty("items")
  public List<@Valid EpgProgramme> getItems() {
    return items;
  }

  public void setItems(List<@Valid EpgProgramme> items) {
    this.items = items;
  }

  public EpgProgrammeList epg(@Nullable EpgImportStatus epg) {
    this.epg = epg;
    return this;
  }

  /**
   * Get epg
   * @return epg
   */
  @Valid 
  @JsonProperty("epg")
  public @Nullable EpgImportStatus getEpg() {
    return epg;
  }

  public void setEpg(@Nullable EpgImportStatus epg) {
    this.epg = epg;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EpgProgrammeList epgProgrammeList = (EpgProgrammeList) o;
    return Objects.equals(this.items, epgProgrammeList.items) &&
        Objects.equals(this.epg, epgProgrammeList.epg);
  }

  @Override
  public int hashCode() {
    return Objects.hash(items, epg);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EpgProgrammeList {\n");
    sb.append("    items: ").append(toIndentedString(items)).append("\n");
    sb.append("    epg: ").append(toIndentedString(epg)).append("\n");
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

