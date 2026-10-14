package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.WatchlistContentType;
import tv.lumo.api.generated.model.WatchlistItem;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * One page of the list. Same envelope as &#x60;PlaybackProgressPage&#x60;, plus &#x60;content_types&#x60;. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class WatchlistPage {

  @Valid
  private List<@Valid WatchlistItem> items = new ArrayList<>();

  private Integer page;

  private Integer size;

  private Long totalElements;

  private Integer totalPages;

  @Valid
  private List<WatchlistContentType> contentTypes = new ArrayList<>();

  public WatchlistPage() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public WatchlistPage(List<@Valid WatchlistItem> items, Integer page, Integer size, Long totalElements, Integer totalPages, List<WatchlistContentType> contentTypes) {
    this.items = items;
    this.page = page;
    this.size = size;
    this.totalElements = totalElements;
    this.totalPages = totalPages;
    this.contentTypes = contentTypes;
  }

  public WatchlistPage items(List<@Valid WatchlistItem> items) {
    this.items = items;
    return this;
  }

  public WatchlistPage addItemsItem(WatchlistItem itemsItem) {
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
  public List<@Valid WatchlistItem> getItems() {
    return items;
  }

  public void setItems(List<@Valid WatchlistItem> items) {
    this.items = items;
  }

  public WatchlistPage page(Integer page) {
    this.page = page;
    return this;
  }

  /**
   * Zero-based index of this page.
   * @return page
   */
  @NotNull 
  @JsonProperty("page")
  public Integer getPage() {
    return page;
  }

  public void setPage(Integer page) {
    this.page = page;
  }

  public WatchlistPage size(Integer size) {
    this.size = size;
    return this;
  }

  /**
   * Requested page size, after the server-side cap.
   * @return size
   */
  @NotNull 
  @JsonProperty("size")
  public Integer getSize() {
    return size;
  }

  public void setSize(Integer size) {
    this.size = size;
  }

  public WatchlistPage totalElements(Long totalElements) {
    this.totalElements = totalElements;
    return this;
  }

  /**
   * Total items matching the filters.
   * @return totalElements
   */
  @NotNull 
  @JsonProperty("total_elements")
  public Long getTotalElements() {
    return totalElements;
  }

  public void setTotalElements(Long totalElements) {
    this.totalElements = totalElements;
  }

  public WatchlistPage totalPages(Integer totalPages) {
    this.totalPages = totalPages;
    return this;
  }

  /**
   * Get totalPages
   * @return totalPages
   */
  @NotNull 
  @JsonProperty("total_pages")
  public Integer getTotalPages() {
    return totalPages;
  }

  public void setTotalPages(Integer totalPages) {
    this.totalPages = totalPages;
  }

  public WatchlistPage contentTypes(List<WatchlistContentType> contentTypes) {
    this.contentTypes = contentTypes;
    return this;
  }

  public WatchlistPage addContentTypesItem(WatchlistContentType contentTypesItem) {
    if (this.contentTypes == null) {
      this.contentTypes = new ArrayList<>();
    }
    this.contentTypes.add(contentTypesItem);
    return this;
  }

  /**
   * The types present in the list for the `sourceId` filter, ignoring `contentType`, unavailable items included. What the Films / Series filter is built from (`WL-17`): a page alone cannot say whether the other type exists further on. 
   * @return contentTypes
   */
  @NotNull @Valid 
  @JsonProperty("content_types")
  public List<WatchlistContentType> getContentTypes() {
    return contentTypes;
  }

  public void setContentTypes(List<WatchlistContentType> contentTypes) {
    this.contentTypes = contentTypes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    WatchlistPage watchlistPage = (WatchlistPage) o;
    return Objects.equals(this.items, watchlistPage.items) &&
        Objects.equals(this.page, watchlistPage.page) &&
        Objects.equals(this.size, watchlistPage.size) &&
        Objects.equals(this.totalElements, watchlistPage.totalElements) &&
        Objects.equals(this.totalPages, watchlistPage.totalPages) &&
        Objects.equals(this.contentTypes, watchlistPage.contentTypes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(items, page, size, totalElements, totalPages, contentTypes);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class WatchlistPage {\n");
    sb.append("    items: ").append(toIndentedString(items)).append("\n");
    sb.append("    page: ").append(toIndentedString(page)).append("\n");
    sb.append("    size: ").append(toIndentedString(size)).append("\n");
    sb.append("    totalElements: ").append(toIndentedString(totalElements)).append("\n");
    sb.append("    totalPages: ").append(toIndentedString(totalPages)).append("\n");
    sb.append("    contentTypes: ").append(toIndentedString(contentTypes)).append("\n");
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

