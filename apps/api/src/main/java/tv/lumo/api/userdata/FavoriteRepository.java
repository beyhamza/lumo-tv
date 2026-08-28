package tv.lumo.api.userdata;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tv.lumo.api.generated.model.Favorite;
import tv.lumo.api.generated.model.FavoriteGroup;

/**
 * Access to {@code favorite} and {@code favorite_group}.
 *
 * <p>Every statement filters on {@code user_id}, including the ones that could
 * have got away with a primary key: {@code favorite.user_id} is denormalised from
 * its group precisely so that this filter never needs a join to be true
 * (docs/architecture.md §2). A favourite id from a URL is therefore never enough
 * on its own, and asking for someone else's is a 404 rather than a deletion.
 */
@Repository
public class FavoriteRepository {

    private final JdbcClient jdbc;

    public FavoriteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---- groups -------------------------------------------------------------

    public List<FavoriteGroup> findGroups(UUID userId) {
        return jdbc.sql("""
                SELECT id, name, position, is_default
                  FROM favorite_group
                 WHERE user_id = :userId
                 ORDER BY position, name
                """)
                .param("userId", userId)
                .query(FavoriteRepository::readGroup)
                .list();
    }

    public Optional<FavoriteGroup> findGroup(UUID groupId, UUID userId) {
        return jdbc.sql("""
                SELECT id, name, position, is_default
                  FROM favorite_group
                 WHERE id = :id AND user_id = :userId
                """)
                .param("id", groupId)
                .param("userId", userId)
                .query(FavoriteRepository::readGroup)
                .optional();
    }

    public Optional<UUID> findOwnedGroupId(UUID groupId, UUID userId) {
        return jdbc.sql("SELECT id FROM favorite_group WHERE id = :id AND user_id = :userId")
                .param("id", groupId)
                .param("userId", userId)
                .query(UUID.class)
                .optional();
    }

    /** @throws org.springframework.dao.DuplicateKeyException on a name already used */
    public FavoriteGroup insertGroup(UUID userId, String name, Integer position) {
        UUID id = UUID.randomUUID();
        int resolved = position == null ? nextGroupPosition(userId) : position;
        jdbc.sql("""
                INSERT INTO favorite_group (id, user_id, name, position, is_default)
                VALUES (:id, :userId, :name, :position, false)
                """)
                .param("id", id)
                .param("userId", userId)
                .param("name", name)
                .param("position", resolved)
                .update();
        return new FavoriteGroup(id, name, resolved, false);
    }

    /**
     * Renames a group, moves it, or both. Absent values leave the column alone.
     *
     * @return the number of rows touched — zero means no such group on this account
     * @throws org.springframework.dao.DuplicateKeyException on a name already used
     */
    public int updateGroup(UUID groupId, UUID userId, String name, Integer position) {
        return jdbc.sql("""
                UPDATE favorite_group
                   SET name     = COALESCE(:name, name),
                       position = COALESCE(:position, position)
                 WHERE id = :id AND user_id = :userId
                """)
                .param("id", groupId)
                .param("userId", userId)
                .param("name", name)
                .param("position", position)
                .update();
    }

    public int deleteGroup(UUID groupId, UUID userId) {
        return jdbc.sql("DELETE FROM favorite_group WHERE id = :id AND user_id = :userId")
                .param("id", groupId)
                .param("userId", userId)
                .update();
    }

    /**
     * Renumbers an account's groups from zero, in their current order.
     *
     * <p>Called after every move so that {@code position} stays contiguous, which
     * is what the contract promises and what lets a client send an index instead
     * of reasoning about the gaps a shift leaves behind.
     */
    public void renumberGroups(UUID userId) {
        jdbc.sql("""
                UPDATE favorite_group g
                   SET position = ordered.rank - 1
                  FROM (SELECT id, row_number() OVER (ORDER BY position, name) AS rank
                          FROM favorite_group
                         WHERE user_id = :userId) ordered
                 WHERE g.id = ordered.id AND g.position <> ordered.rank - 1
                """)
                .param("userId", userId)
                .update();
    }

    /** Opens a gap at {@code position} so the moved group can take that index. */
    public void shiftGroupsFrom(UUID userId, UUID excludedId, int position) {
        jdbc.sql("""
                UPDATE favorite_group
                   SET position = position + 1
                 WHERE user_id = :userId AND id <> :excludedId AND position >= :position
                """)
                .param("userId", userId)
                .param("excludedId", excludedId)
                .param("position", position)
                .update();
    }

    /**
     * The group a favourite lands in when the client names none.
     *
     * <p>Created on first use rather than at registration: an account that never
     * favourites anything never gets a row, and the group appears the moment it
     * has something in it.
     *
     * <p><b>Found by its flag, not by its name.</b> The name is the user's the
     * moment they change it, and looking the group up by the label the server
     * happened to give it meant that renaming it produced a second one on the
     * next add. {@code is_default} is what the partial unique index constrains
     * and what this reads.
     *
     * <p>Two adds racing on a fresh account — a phone and a television starring
     * something at the same instant — meet on the {@code (user_id, name)} index,
     * and {@code DO UPDATE} makes the loser adopt the row the winner inserted
     * instead of failing on a duplicate. The same clause covers the odd case of
     * an account that already has a group it named {@code Favorites} itself: that
     * group is promoted rather than duplicated.
     */
    public UUID findOrCreateDefaultGroup(UUID userId, String name) {
        Optional<UUID> existing = jdbc.sql("""
                SELECT id FROM favorite_group WHERE user_id = :userId AND is_default
                """)
                .param("userId", userId)
                .query(UUID.class)
                .optional();
        if (existing.isPresent()) {
            return existing.get();
        }

        return jdbc.sql("""
                INSERT INTO favorite_group (id, user_id, name, position, is_default)
                VALUES (:id, :userId, :name, 0, true)
                ON CONFLICT (user_id, name) DO UPDATE SET is_default = true
                RETURNING id
                """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("name", name)
                .query(UUID.class)
                .single();
    }

    private static FavoriteGroup readGroup(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new FavoriteGroup(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getInt("position"),
                rs.getBoolean("is_default"));
    }

    private int nextGroupPosition(UUID userId) {
        return jdbc.sql("""
                SELECT COALESCE(max(position) + 1, 0) FROM favorite_group WHERE user_id = :userId
                """)
                .param("userId", userId)
                .query(Integer.class)
                .single();
    }

    // ---- favourites ---------------------------------------------------------

    /** @param groupId null lists every group */
    public List<Favorite> findFavorites(UUID userId, UUID groupId) {
        return jdbc.sql("""
                SELECT f.id, f.group_id, f.source_id, f.channel_id, f.position
                  FROM favorite f
                  JOIN favorite_group g ON g.id = f.group_id
                 WHERE f.user_id = :userId
                   AND (:groupId::uuid IS NULL OR f.group_id = :groupId)
                 ORDER BY g.position, f.position
                """)
                .param("userId", userId)
                .param("groupId", groupId)
                .query((rs, n) -> new Favorite(
                        rs.getObject("id", UUID.class),
                        rs.getObject("group_id", UUID.class),
                        rs.getObject("source_id", UUID.class),
                        rs.getObject("channel_id", UUID.class),
                        rs.getInt("position")))
                .list();
    }

    /** @throws org.springframework.dao.DuplicateKeyException when the channel is already in that group */
    public Favorite insert(UUID userId, UUID groupId, UUID sourceId, UUID channelId, Integer position) {
        UUID id = UUID.randomUUID();
        int resolved = position == null ? nextFavoritePosition(groupId) : position;
        jdbc.sql("""
                INSERT INTO favorite (id, group_id, user_id, source_id, channel_id, position)
                VALUES (:id, :groupId, :userId, :sourceId, :channelId, :position)
                """)
                .param("id", id)
                .param("groupId", groupId)
                .param("userId", userId)
                .param("sourceId", sourceId)
                .param("channelId", channelId)
                .param("position", resolved)
                .update();
        return new Favorite(id, groupId, sourceId, channelId, resolved);
    }

    public int delete(UUID favoriteId, UUID userId) {
        return jdbc.sql("DELETE FROM favorite WHERE id = :id AND user_id = :userId")
                .param("id", favoriteId)
                .param("userId", userId)
                .update();
    }

    public Optional<Favorite> findFavorite(UUID favoriteId, UUID userId) {
        return jdbc.sql("""
                SELECT id, group_id, source_id, channel_id, position
                  FROM favorite
                 WHERE id = :id AND user_id = :userId
                """)
                .param("id", favoriteId)
                .param("userId", userId)
                .query((rs, n) -> new Favorite(
                        rs.getObject("id", UUID.class),
                        rs.getObject("group_id", UUID.class),
                        rs.getObject("source_id", UUID.class),
                        rs.getObject("channel_id", UUID.class),
                        rs.getInt("position")))
                .optional();
    }

    /** @throws org.springframework.dao.DuplicateKeyException when the channel is already in the target group */
    public void moveFavorite(UUID favoriteId, UUID groupId, int position) {
        jdbc.sql("UPDATE favorite SET group_id = :groupId, position = :position WHERE id = :id")
                .param("id", favoriteId)
                .param("groupId", groupId)
                .param("position", position)
                .update();
    }

    /** Opens a gap at {@code position} so the moved favourite can take that index. */
    public void shiftFavoritesFrom(UUID groupId, UUID excludedId, int position) {
        jdbc.sql("""
                UPDATE favorite
                   SET position = position + 1
                 WHERE group_id = :groupId AND id <> :excludedId AND position >= :position
                """)
                .param("groupId", groupId)
                .param("excludedId", excludedId)
                .param("position", position)
                .update();
    }

    /** Renumbers a group's favourites from zero, in their current order. */
    public void renumberFavorites(UUID groupId) {
        jdbc.sql("""
                UPDATE favorite f
                   SET position = ordered.rank - 1
                  FROM (SELECT id, row_number() OVER (ORDER BY position, id) AS rank
                          FROM favorite
                         WHERE group_id = :groupId) ordered
                 WHERE f.id = ordered.id AND f.position <> ordered.rank - 1
                """)
                .param("groupId", groupId)
                .update();
    }

    public int countFavorites(UUID groupId) {
        return jdbc.sql("SELECT count(*) FROM favorite WHERE group_id = :groupId")
                .param("groupId", groupId)
                .query(Integer.class)
                .single();
    }

    /**
     * Drops the favourites of {@code groupId} whose channel is already starred in
     * {@code targetGroupId}.
     *
     * <p>Called just before emptying one group into another. Without it the move
     * would hit {@code favorite_group_channel_key} and fail a deletion the user
     * has already confirmed. Dropping the row loses nothing: the channel stays a
     * favourite, in the group it was already in.
     */
    public int deleteFavoritesAlreadyIn(UUID groupId, UUID targetGroupId) {
        return jdbc.sql("""
                DELETE FROM favorite f
                 WHERE f.group_id = :groupId
                   AND EXISTS (SELECT 1 FROM favorite t
                                WHERE t.group_id = :targetGroupId
                                  AND t.channel_id = f.channel_id)
                """)
                .param("groupId", groupId)
                .param("targetGroupId", targetGroupId)
                .update();
    }

    /** Moves every favourite of a group into another, appended after what is already there. */
    public int moveFavoritesToGroup(UUID groupId, UUID targetGroupId) {
        return jdbc.sql("""
                UPDATE favorite f
                   SET group_id = :targetGroupId,
                       position = :offset + ordered.rank - 1
                  FROM (SELECT id, row_number() OVER (ORDER BY position, id) AS rank
                          FROM favorite
                         WHERE group_id = :groupId) ordered
                 WHERE f.id = ordered.id
                """)
                .param("groupId", groupId)
                .param("targetGroupId", targetGroupId)
                .param("offset", nextFavoritePosition(targetGroupId))
                .update();
    }

    private int nextFavoritePosition(UUID groupId) {
        return jdbc.sql("""
                SELECT COALESCE(max(position) + 1, 0) FROM favorite WHERE group_id = :groupId
                """)
                .param("groupId", groupId)
                .query(Integer.class)
                .single();
    }
}
