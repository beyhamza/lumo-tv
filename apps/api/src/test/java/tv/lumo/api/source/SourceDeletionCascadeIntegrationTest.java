package tv.lumo.api.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.auth.UserRow;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * What deleting a source takes with it (US-024, lot C4).
 *
 * <p>The contract lists it: categories, channels, EPG programmes, films, series
 * with their seasons and episodes, favourites, recently watched channels and
 * playback progress — and nothing that belongs to another source. None of that
 * is application code. It is nine {@code ON DELETE CASCADE} clauses spread over
 * six changesets written months apart, and the way one goes missing is a new
 * table that references {@code source} without it: the delete then fails on a
 * foreign key, in production, for the one user who has a row in that table.
 *
 * <p>So the test does not check the clauses; it fills every table for two
 * sources, deletes one through the service, and counts.
 */
@Import(PostgresContainerInitializer.class)
class SourceDeletionCascadeIntegrationTest extends PostgresIntegrationTest {

    /** Every table that holds rows belonging to a source, directly or not. */
    private static final List<String> TABLES_BY_SOURCE = List.of(
            "category", "channel", "epg_programme", "vod_item", "series", "episode",
            "favorite", "recent_channel", "playback_progress");

    @Autowired
    private SourceService sourceService;

    @Autowired
    private SourceRepository sources;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    private UserRow user;
    private UUID deleted;
    private UUID kept;
    private UUID groupId;
    /** Source id → the id of its one season, which carries no source_id to count by. */
    private final Map<UUID, UUID> seasonOf = new HashMap<>();

    @BeforeEach
    void fillTwoSources() {
        user = users.insert(UUID.randomUUID(), "cascade-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Cascade Test", "fr");
        groupId = UUID.randomUUID();
        jdbc.sql("INSERT INTO favorite_group (id, user_id, name) VALUES (:id, :userId, 'Favoris')")
                .param("id", groupId).param("userId", user.id()).update();

        deleted = fill("Source supprimée");
        kept = fill("Source conservée");
    }

    @Test
    @DisplayName("supprimer une source emporte tout ce que le contrat annonce")
    void deletionTakesEverythingTheContractLists() {
        sourceService.delete(deleted, user.id());

        for (String table : TABLES_BY_SOURCE) {
            assertThat(countBySource(table, deleted))
                    .as("%s must hold nothing of the deleted source", table)
                    .isZero();
        }
        // `season` has no source_id of its own: it goes through its series.
        assertThat(seasonExists(deleted)).isFalse();
    }

    @Test
    @DisplayName("rien de l'autre source du même compte ne bouge")
    void theOtherSourceIsUntouched() {
        sourceService.delete(deleted, user.id());

        for (String table : TABLES_BY_SOURCE) {
            assertThat(countBySource(table, kept))
                    .as("%s must still hold the row of the source that was kept", table)
                    .isEqualTo(1);
        }
        assertThat(seasonExists(kept)).isTrue();
        // The group is the user's, not the source's: it survives with whatever
        // the other source still has in it.
        assertThat(jdbc.sql("SELECT count(*) FROM favorite_group WHERE id = :id")
                .param("id", groupId).query(Long.class).single()).isEqualTo(1);
    }

    // ---- seeding ------------------------------------------------------------

    /** One row in every table, all belonging to one new source. */
    private UUID fill(String label) {
        UUID sourceId = UUID.randomUUID();
        sources.insert(sourceId, user.id(), label, SourceKind.M3U_URL, null, null, null,
                "https://playlist.example/" + sourceId + ".m3u", null, null, null);

        UUID categoryId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO category (id, source_id, external_id, name, content_type, position)
                VALUES (:id, :sourceId, :externalId, 'Groupe 01', 'LIVE', 0)
                """)
                .param("id", categoryId).param("sourceId", sourceId)
                .param("externalId", "test:" + categoryId).update();

        UUID channelId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO channel (id, source_id, category_id, external_id, name, stream_url, position)
                VALUES (:id, :sourceId, :categoryId, :externalId, 'Chaîne 01',
                        'https://stream.example/x.m3u8', 0)
                """)
                .param("id", channelId).param("sourceId", sourceId).param("categoryId", categoryId)
                .param("externalId", "test:" + channelId).update();

        jdbc.sql("""
                INSERT INTO epg_programme (id, source_id, tvg_id, starts_at, ends_at, title)
                VALUES (:id, :sourceId, 'chaine01.test', now(), now() + interval '1 hour', 'Programme 01')
                """)
                .param("id", UUID.randomUUID()).param("sourceId", sourceId).update();

        UUID filmId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO vod_item (id, source_id, external_id, name, stream_url,
                                      container_extension, position)
                VALUES (:id, :sourceId, :externalId, 'Le Voyage',
                        'https://stream.example/movie.mkv', 'mkv', 0)
                """)
                .param("id", filmId).param("sourceId", sourceId)
                .param("externalId", "test:" + filmId).update();

        UUID seriesId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO series (id, source_id, external_id, name, position)
                VALUES (:id, :sourceId, :externalId, 'La Série', 0)
                """)
                .param("id", seriesId).param("sourceId", sourceId)
                .param("externalId", "test:" + seriesId).update();

        UUID seasonId = UUID.randomUUID();
        jdbc.sql("INSERT INTO season (id, series_id, season_number) VALUES (:id, :seriesId, 1)")
                .param("id", seasonId).param("seriesId", seriesId).update();
        seasonOf.put(sourceId, seasonId);

        jdbc.sql("""
                INSERT INTO episode (id, series_id, season_id, source_id, external_id,
                                     season_number, episode_number, stream_url)
                VALUES (:id, :seriesId, :seasonId, :sourceId, :externalId, 1, 1,
                        'https://stream.example/episode.mkv')
                """)
                .param("id", UUID.randomUUID()).param("seriesId", seriesId)
                .param("seasonId", seasonId).param("sourceId", sourceId)
                .param("externalId", "test:" + seasonId).update();

        jdbc.sql("""
                INSERT INTO favorite (id, group_id, user_id, source_id, channel_id, position)
                VALUES (:id, :groupId, :userId, :sourceId, :channelId, 0)
                """)
                .param("id", UUID.randomUUID()).param("groupId", groupId).param("userId", user.id())
                .param("sourceId", sourceId).param("channelId", channelId).update();

        jdbc.sql("""
                INSERT INTO recent_channel (id, user_id, source_id, channel_id)
                VALUES (:id, :userId, :sourceId, :channelId)
                """)
                .param("id", UUID.randomUUID()).param("userId", user.id())
                .param("sourceId", sourceId).param("channelId", channelId).update();

        jdbc.sql("""
                INSERT INTO playback_progress (id, user_id, source_id, item_type, item_ref, position_ms)
                VALUES (:id, :userId, :sourceId, 'VOD', :itemRef, 60000)
                """)
                .param("id", UUID.randomUUID()).param("userId", user.id())
                .param("sourceId", sourceId).param("itemRef", filmId.toString()).update();

        return sourceId;
    }

    private long countBySource(String table, UUID sourceId) {
        // `table` comes from the constant above, never from input.
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE source_id = :sourceId")
                .param("sourceId", sourceId).query(Long.class).single();
    }

    private boolean seasonExists(UUID sourceId) {
        return jdbc.sql("SELECT count(*) FROM season WHERE id = :id")
                .param("id", seasonOf.get(sourceId)).query(Long.class).single() == 1;
    }
}
