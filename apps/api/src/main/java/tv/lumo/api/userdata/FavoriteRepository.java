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
                SELECT id, name, position
                  FROM favorite_group
                 WHERE user_id = :userId
                 ORDER BY position, name
                """)
                .param("userId", userId)
                .query((rs, n) -> new FavoriteGroup(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getInt("position")))
                .list();
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
                INSERT INTO favorite_group (id, user_id, name, position)
                VALUES (:id, :userId, :name, :position)
                """)
                .param("id", id)
                .param("userId", userId)
                .param("name", name)
                .param("position", resolved)
                .update();
        return new FavoriteGroup(id, name, resolved);
    }

    /**
     * The group a favourite lands in when the client names none.
     *
     * <p>Created on first use rather than at registration: an account that never
     * favourites anything never gets a row, and the group appears the moment it
     * has something in it.
     *
     * <p>{@code ON CONFLICT DO NOTHING} on {@code (user_id, name)} makes this safe
     * against two simultaneous first adds — the phone and the television both
     * starring something at once — which would otherwise be a unique-violation
     * 500 on whichever lost.
     */
    public UUID findOrCreateDefaultGroup(UUID userId, String name) {
        jdbc.sql("""
                INSERT INTO favorite_group (id, user_id, name, position)
                VALUES (:id, :userId, :name, 0)
                ON CONFLICT (user_id, name) DO NOTHING
                """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("name", name)
                .update();

        return jdbc.sql("SELECT id FROM favorite_group WHERE user_id = :userId AND name = :name")
                .param("userId", userId)
                .param("name", name)
                .query(UUID.class)
                .single();
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

    private int nextFavoritePosition(UUID groupId) {
        return jdbc.sql("""
                SELECT COALESCE(max(position) + 1, 0) FROM favorite WHERE group_id = :groupId
                """)
                .param("groupId", groupId)
                .query(Integer.class)
                .single();
    }
}
