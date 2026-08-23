package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.FavoriteGroup;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * FavoriteGroupList
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class FavoriteGroupList {

  @Valid
  private List<@Valid FavoriteGroup> items = new ArrayList<>();

  public FavoriteGroupList() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public FavoriteGroupList(List<@Valid FavoriteGroup> items) {
    this.items = items;
  }

  public FavoriteGroupList items(List<@Valid FavoriteGroup> items) {
    this.items = items;
    return this;
  }

  public FavoriteGroupList addItemsItem(FavoriteGroup itemsItem) {
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
  public List<@Valid FavoriteGroup> getItems() {
    return items;
  }

  public void setItems(List<@Valid FavoriteGroup> items) {
    this.items = items;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FavoriteGroupList favoriteGroupList = (FavoriteGroupList) o;
    return Objects.equals(this.items, favoriteGroupList.items);
  }

  @Override
  public int hashCode() {
    return Objects.hash(items);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class FavoriteGroupList {\n");
    sb.append("    items: ").append(toIndentedString(items)).append("\n");
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

