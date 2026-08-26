package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.lang.Nullable;
import tv.lumo.api.generated.model.PlaybackProgress;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * One page of saved positions. Same shape as &#x60;ChannelPage&#x60;, on purpose: one pagination envelope to learn, not two. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class PlaybackProgressPage {

  @Valid
  private List<@Valid PlaybackProgress> items = new ArrayList<>();

  private Integer page;

  private Integer size;

  private Long totalElements;

  private Integer totalPages;

  public PlaybackProgressPage() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public PlaybackProgressPage(List<@Valid PlaybackProgress> items, Integer page, Integer size, Long totalElements, Integer totalPages) {
    this.items = items;
    this.page = page;
    this.size = size;
    this.totalElements = totalElements;
    this.totalPages = totalPages;
  }

  public PlaybackProgressPage items(List<@Valid PlaybackProgress> items) {
    this.items = items;
    return this;
  }

  public PlaybackProgressPage addItemsItem(PlaybackProgress itemsItem) {
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
  public List<@Valid PlaybackProgress> getItems() {
    return items;
  }

  public void setItems(List<@Valid PlaybackProgress> items) {
    this.items = items;
  }

  public PlaybackProgressPage page(Integer page) {
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

  public PlaybackProgressPage size(Integer size) {
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

  public PlaybackProgressPage totalElements(Long totalElements) {
    this.totalElements = totalElements;
    return this;
  }

  /**
   * Total rows matching the filters.
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

  public PlaybackProgressPage totalPages(Integer totalPages) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PlaybackProgressPage playbackProgressPage = (PlaybackProgressPage) o;
    return Objects.equals(this.items, playbackProgressPage.items) &&
        Objects.equals(this.page, playbackProgressPage.page) &&
        Objects.equals(this.size, playbackProgressPage.size) &&
        Objects.equals(this.totalElements, playbackProgressPage.totalElements) &&
        Objects.equals(this.totalPages, playbackProgressPage.totalPages);
  }

  @Override
  public int hashCode() {
    return Objects.hash(items, page, size, totalElements, totalPages);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PlaybackProgressPage {\n");
    sb.append("    items: ").append(toIndentedString(items)).append("\n");
    sb.append("    page: ").append(toIndentedString(page)).append("\n");
    sb.append("    size: ").append(toIndentedString(size)).append("\n");
    sb.append("    totalElements: ").append(toIndentedString(totalElements)).append("\n");
    sb.append("    totalPages: ").append(toIndentedString(totalPages)).append("\n");
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

