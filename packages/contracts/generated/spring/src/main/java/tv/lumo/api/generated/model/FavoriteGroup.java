package tv.lumo.api.generated.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.UUID;
import org.springframework.lang.Nullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * A user-defined grouping of favourites. A default group is created on the first add.  A group belongs to the **account**, not to a source: one group holds channels from several subscriptions, and it survives a re-synchronisation whole. 
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.14.0")
public class FavoriteGroup {

  private UUID id;

  private String name;

  private Integer position;

  private Boolean isDefault;

  public FavoriteGroup() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public FavoriteGroup(UUID id, String name, Integer position, Boolean isDefault) {
    this.id = id;
    this.name = name;
    this.position = position;
    this.isDefault = isDefault;
  }

  public FavoriteGroup id(UUID id) {
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

  public FavoriteGroup name(String name) {
    this.name = name;
    return this;
  }

  /**
   * Get name
   * @return name
   */
  @NotNull 
  @JsonProperty("name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public FavoriteGroup position(Integer position) {
    this.position = position;
    return this;
  }

  /**
   * Get position
   * @return position
   */
  @NotNull 
  @JsonProperty("position")
  public Integer getPosition() {
    return position;
  }

  public void setPosition(Integer position) {
    this.position = position;
  }

  public FavoriteGroup isDefault(Boolean isDefault) {
    this.isDefault = isDefault;
    return this;
  }

  /**
   * True for the one group created on the first add — where `POST /me/favorites` lands without a `group_id`, and where `DELETE /me/favorite-groups/{id}` empties the others.  **Why a flag and not a sentinel in `name`.** The server has to call that group something, and it calls it `Favorites`, in English: a user-visible string in one language, which no client could translate because nothing marked it as the default one. The same problem was solved once for M3U entries with no `group-title`, by a sentinel in `external_id` — but `name` here belongs to the user the moment they rename it, and a client must still know which group is the default afterwards. A flag survives the rename; a sentinel would not.  A client renders its own wording while this is true **and** the name is still the server's; once the user has renamed the group, their name wins. 
   * @return isDefault
   */
  @NotNull 
  @JsonProperty("is_default")
  public Boolean getIsDefault() {
    return isDefault;
  }

  public void setIsDefault(Boolean isDefault) {
    this.isDefault = isDefault;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FavoriteGroup favoriteGroup = (FavoriteGroup) o;
    return Objects.equals(this.id, favoriteGroup.id) &&
        Objects.equals(this.name, favoriteGroup.name) &&
        Objects.equals(this.position, favoriteGroup.position) &&
        Objects.equals(this.isDefault, favoriteGroup.isDefault);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, position, isDefault);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class FavoriteGroup {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    position: ").append(toIndentedString(position)).append("\n");
    sb.append("    isDefault: ").append(toIndentedString(isDefault)).append("\n");
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

