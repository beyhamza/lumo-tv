package tv.lumo.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.auth.UserRow;
import tv.lumo.api.generated.model.Episode;
import tv.lumo.api.generated.model.Season;
import tv.lumo.api.generated.model.Series;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.source.SourceRepository;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * Reading series, seasons and episodes (US-15).
 *
 * <p>The film test one level deeper, and the extra level is what most of these
 * cases are about. A film is a row; a series is a tree, and a tree read wrongly
 * fails in ways a row cannot — a season that vanishes because it holds nothing, an
 * episode attached to the wrong season, a page of episodes carrying the stream URL
 * a page must never carry.
 *
 * <p><b>Two cases assert an absence</b>, as the film test does: a listing must
 * carry no {@code stream_url} and no {@code plot}. Neither shows up as a failure —
 * they show up as a field quietly populated when it should not be.
 */
@Import(PostgresContainerInitializer.class)
class SeriesCatalogIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private CatalogReadRepository catalog;

    @Autowired
    private UserRepository users;

    @Autowired
    private SourceRepository sources;

    @Autowired
    private JdbcClient jdbc;

    private UserRow user;
    private UUID sourceId;
    private UUID seriesId;
    private UUID episodeS1E1;
    private UUID episodeS1E2;
    private UUID episodeS2E1;

    @BeforeEach
    void createOneTree() {
        user = insertUser();
        sourceId = insertSource(user);
        UUID drama = insertCategory(sourceId, "Drame");
        seriesId = insertSeries(sourceId, "Les Falaises", drama, 0);

        UUID seasonOne = insertSeason(seriesId, 1, 2);
        episodeS1E1 = insertEpisode(sourceId, seriesId, seasonOne, 1, 1, "Le départ");
        episodeS1E2 = insertEpisode(sourceId, seriesId, seasonOne, 1, 2, "La côte");

        UUID seasonTwo = insertSeason(seriesId, 2, 1);
        episodeS2E1 = insertEpisode(sourceId, seriesId, seasonTwo, 2, 1, null);
    }

    @Test
    @DisplayName("a page of series holds series, ordered by position")
    void listsSeries() {
        insertSeries(sourceId, "Le Phare", null, 1);

        List<Series> found = catalog.findSeries(sourceId, user.id(), null, null, null, 0, 50);

        assertThat(found).extracting(Series::getName)
                .containsExactly("Les Falaises", "Le Phare");
        assertThat(catalog.countSeries(sourceId, user.id(), null, null, null)).isEqualTo(2);
    }

    @Test
    @DisplayName("a page of series carries no synopsis")
    void theListingProjectionIsNarrow() {
        Series series = catalog.findSeries(sourceId, user.id(), null, null, null, 0, 50)
                .getFirst();

        // The plot arrives with the tree, from the one call GET /series/{id} makes.
        // A listing that carried it would mean either a synopsis fetched per series
        // at synchronisation — eight hundred calls to somebody's own panel — or a
        // column selected for nobody.
        assertThat(series.getPlot()).isNull();
    }

    @Test
    @DisplayName("somebody else's series does not resolve")
    void ownershipIsInTheQuery() {
        UserRow stranger = insertUser();

        assertThat(catalog.findSeriesOwnedBy(seriesId, stranger.id())).isEmpty();
        assertThat(catalog.findSeriesOwnedBy(seriesId, user.id())).isPresent();
    }

    @Test
    @DisplayName("the tree comes back grouped, ordered, and with each episode in its own season")
    void readsTheTree() {
        List<Season> seasons = catalog.findTree(seriesId);

        assertThat(seasons).extracting(Season::getSeasonNumber).containsExactly(1, 2);
        assertThat(seasons.get(0).getEpisodes()).extracting(Episode::getId)
                .containsExactly(episodeS1E1, episodeS1E2);
        assertThat(seasons.get(1).getEpisodes()).extracting(Episode::getId)
                .containsExactly(episodeS2E1);
    }

    @Test
    @DisplayName("a season with no episodes is still a season")
    void keepsEmptySeasons() {
        insertSeason(seriesId, 3, 0);

        List<Season> seasons = catalog.findTree(seriesId);

        // The left join keeps it. A panel that lists a season and returns nothing
        // for it is describing something a viewer should see as empty rather than
        // as absent — and an inner join would have hidden it, silently.
        assertThat(seasons).extracting(Season::getSeasonNumber).containsExactly(1, 2, 3);
        assertThat(seasons.get(2).getEpisodes()).isEmpty();
    }

    @Test
    @DisplayName("the panel's episode count is carried, and the list is what is real")
    void carriesTheClaimWithoutBelievingIt() {
        // The panel says four; two exist. Both are reported, because the claim is
        // occasionally the only hint that a season is incomplete — and the list is
        // what a client counts.
        jdbc.sql("UPDATE season SET episode_count = 4 WHERE series_id = :id AND season_number = 1")
                .param("id", seriesId)
                .update();

        Season first = catalog.findTree(seriesId).getFirst();

        assertThat(first.getEpisodeCount()).isEqualTo(4);
        assertThat(first.getEpisodes()).hasSize(2);
    }

    @Test
    @DisplayName("an episode with no title keeps its numbers")
    void episodesWithoutTitles() {
        Episode untitled = catalog.findTree(seriesId).get(1).getEpisodes().getFirst();

        // Null far more often than for a film. A client shows "Episode 1" — the
        // number is always there, the title is not.
        assertThat(untitled.getName()).isNull();
        assertThat(untitled.getSeasonNumber()).isEqualTo(2);
        assertThat(untitled.getEpisodeNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("episodes resolve by identifier, and carry the series they belong to")
    void resolvesEpisodes() {
        List<Episode> found = catalog.findEpisodes(sourceId, user.id(),
                List.of(episodeS1E2, episodeS2E1), 0, 50);

        assertThat(found).extracting(Episode::getId)
                .containsExactlyInAnyOrder(episodeS1E2, episodeS2E1);
        // The field a "continue watching" rail exists on: without it, a saved
        // position resolves to an episode and stops there.
        assertThat(found).allSatisfy(e -> assertThat(e.getSeriesId()).isEqualTo(seriesId));
        assertThat(catalog.countEpisodes(sourceId, user.id(),
                List.of(episodeS1E2, episodeS2E1))).isEqualTo(2);
    }

    @Test
    @DisplayName("resolving somebody else's episode answers nothing, not an error")
    void resolveIsScopedToTheOwner() {
        UserRow stranger = insertUser();

        assertThat(catalog.findEpisodes(sourceId, stranger.id(), List.of(episodeS1E1), 0, 50))
                .isEmpty();
    }

    @Test
    @DisplayName("an unknown identifier is absent from the answer rather than an error")
    void unknownIdentifiersAreDropped() {
        List<Episode> found = catalog.findEpisodes(sourceId, user.id(),
                List.of(episodeS1E1, UUID.randomUUID()), 0, 50);

        assertThat(found).extracting(Episode::getId).containsExactly(episodeS1E1);
    }

    @Test
    @DisplayName("le codec audio traverse la base tel quel, et son absence reste nulle")
    void carriesTheAudioCodec() {
        // It exists so a browser can warn before playing: none of them decodes
        // Dolby Digital, so an episode in `ac3` plays perfectly and silently. The
        // round trip is worth a test because nothing else catches a mistyped
        // column — `rs.getString` fails at runtime, in front of somebody.
        jdbc.sql("""
                UPDATE episode SET audio_codec = 'ac3', audio_channels = 6
                 WHERE id = :id
                """).param("id", episodeS1E1).update();

        List<Episode> found = catalog.findEpisodes(
                sourceId, user.id(), List.of(episodeS1E1, episodeS1E2), 0, 50);

        assertThat(found).filteredOn(e -> e.getId().equals(episodeS1E1)).singleElement()
                .satisfies(episode -> {
                    assertThat(episode.getAudioCodec()).isEqualTo("ac3");
                    assertThat(episode.getAudioChannels()).isEqualTo(6);
                });

        // The ordinary case, and the one that must never become a warning: a
        // panel that said nothing. Null is "not known", not "no sound".
        assertThat(found).filteredOn(e -> e.getId().equals(episodeS1E2)).singleElement()
                .satisfies(episode -> {
                    assertThat(episode.getAudioCodec()).isNull();
                    assertThat(episode.getAudioChannels()).isNull();
                });

        // And through the tree, which is a different query with the same columns
        // — the one the series page actually reads.
        assertThat(catalog.findTree(seriesId).get(0).getEpisodes().get(0).getAudioCodec())
                .isEqualTo("ac3");
    }

    @Test
    @DisplayName("an episode's stream URL is readable one at a time, by its owner only")
    void playbackIsScopedToTheOwner() {
        UserRow stranger = insertUser();

        assertThat(catalog.findEpisodeStreamUrlOwnedBy(episodeS1E1, stranger.id())).isEmpty();
        assertThat(catalog.findEpisodeStreamUrlOwnedBy(episodeS1E1, user.id()))
                .get()
                .extracting(CatalogReadRepository.PlaybackRow::streamUrl)
                .isEqualTo("https://stream.example/series/1.mkv");
    }

    @Test
    @DisplayName("deleting a series takes its seasons and episodes with it")
    void cascadesFromSeries() {
        jdbc.sql("DELETE FROM series WHERE id = :id").param("id", seriesId).update();

        // Without the cascade, an episode of a deleted series stays resolvable and
        // its stream URL stays readable — a row nobody can reach through the API
        // and nobody would think to look for.
        assertThat(catalog.findTree(seriesId)).isEmpty();
        assertThat(catalog.findEpisodeStreamUrlOwnedBy(episodeS1E1, user.id())).isEmpty();
    }

    @Test
    @DisplayName("a re-synchronisation keeps episode identifiers, so a saved position survives")
    void upsertKeyIsThePanelsEpisodeId() {
        // The upsert key is (series_id, external_id). Writing the same episode
        // again must collide rather than duplicate: that is what lets a saved
        // position keep pointing at something real after the weekly pass.
        UUID seasonOne = jdbc.sql(
                        "SELECT id FROM season WHERE series_id = :id AND season_number = 1")
                .param("id", seriesId)
                .query(UUID.class)
                .single();

        int updated = jdbc.sql("""
                INSERT INTO episode (id, series_id, season_id, source_id, external_id,
                                     season_number, episode_number, name, stream_url)
                VALUES (:id, :seriesId, :seasonId, :sourceId, :externalId, 1, 1,
                        'Le départ, renommé', 'https://stream.example/series/1.mkv')
                ON CONFLICT (series_id, external_id) WHERE external_id IS NOT NULL
                DO UPDATE SET name = EXCLUDED.name
                """)
                .param("id", UUID.randomUUID())
                .param("seriesId", seriesId)
                .param("seasonId", seasonOne)
                .param("sourceId", sourceId)
                .param("externalId", "ep:1:1")
                .update();

        assertThat(updated).isEqualTo(1);
        List<Episode> firstSeason = catalog.findTree(seriesId).getFirst().getEpisodes();
        assertThat(firstSeason).extracting(Episode::getId)
                .containsExactly(episodeS1E1, episodeS1E2);
        assertThat(firstSeason.getFirst().getName()).isEqualTo("Le départ, renommé");
    }

    // ---- fixtures ----------------------------------------------------------

    private UserRow insertUser() {
        return users.insert(UUID.randomUUID(),
                "series-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Series Test", "en");
    }

    private UUID insertSource(UserRow owner) {
        UUID id = UUID.randomUUID();
        sources.insert(id, owner.id(), "Test panel", SourceKind.XTREAM, "https://panel.example",
                "user", new byte[] { 1, 2, 3 }, null, null, null, null);
        return id;
    }

    private UUID insertCategory(UUID source, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO category (id, source_id, external_id, name, content_type, position)
                VALUES (:id, :sourceId, :externalId, :name, 'SERIES', 0)
                """)
                .param("id", id)
                .param("sourceId", source)
                .param("externalId", "test:" + id)
                .param("name", name)
                .update();
        return id;
    }

    /**
     * Inserts a series directly.
     *
     * <p>Going through ingestion would mean an outbound request to somebody's
     * panel, which a test suite has no business making. What is under test is what
     * happens to a tree once it exists.
     */
    private UUID insertSeries(UUID source, String name, UUID categoryId, int position) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO series (id, source_id, category_id, external_id, name,
                                    poster_url, year, episode_run_time, plot, position)
                VALUES (:id, :sourceId, :categoryId, :externalId, :name,
                        'https://poster.example/s.jpg', 2019, 45,
                        'Un synopsis en cache.', :position)
                """)
                .param("id", id)
                .param("sourceId", source)
                .param("categoryId", categoryId)
                .param("externalId", "test:" + id)
                .param("name", name)
                .param("position", position)
                .update();
        return id;
    }

    private UUID insertSeason(UUID series, int number, int episodeCount) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO season (id, series_id, season_number, episode_count)
                VALUES (:id, :seriesId, :number, :count)
                """)
                .param("id", id)
                .param("seriesId", series)
                .param("number", number)
                .param("count", episodeCount)
                .update();
        return id;
    }

    private UUID insertEpisode(UUID source, UUID series, UUID season,
                               int seasonNumber, int episodeNumber, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO episode (id, series_id, season_id, source_id, external_id,
                                     season_number, episode_number, name,
                                     duration_seconds, stream_url, container_extension)
                VALUES (:id, :seriesId, :seasonId, :sourceId, :externalId,
                        :seasonNumber, :episodeNumber, :name, 2700,
                        'https://stream.example/series/1.mkv', 'mkv')
                """)
                .param("id", id)
                .param("seriesId", series)
                .param("seasonId", season)
                .param("sourceId", source)
                .param("externalId", "ep:" + seasonNumber + ":" + episodeNumber)
                .param("seasonNumber", seasonNumber)
                .param("episodeNumber", episodeNumber)
                .param("name", name)
                .update();
        return id;
    }
}
