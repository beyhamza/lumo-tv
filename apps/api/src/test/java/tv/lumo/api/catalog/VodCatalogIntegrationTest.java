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
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.generated.model.VodItem;
import tv.lumo.api.source.SourceRepository;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * Reading films (US-13).
 *
 * <p>An integration test rather than a unit one, for the reason
 * {@link ChannelLookupIntegrationTest} gives: everything worth asserting here is
 * the database's behaviour — the ownership join that decides what somebody else's
 * identifier resolves to, and the two projections that decide what a listing is
 * allowed to carry.
 *
 * <p><b>Two of these cases are about what the SQL does not select.</b> A page of
 * films must not carry a stream URL, and it must not carry a synopsis: the first
 * is the rule that keeps a thousand credential-bearing URLs out of one response,
 * the second is what stops a catalogue of thirty thousand from costing thirty
 * thousand calls to somebody's own provider. Neither shows up as a failure — they
 * show up as a field that is quietly populated when it should not be.
 */
@Import(PostgresContainerInitializer.class)
class VodCatalogIntegrationTest extends PostgresIntegrationTest {

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
    private UUID actionId;
    private UUID first;
    private UUID second;
    private UUID strangerFilmId;

    @BeforeEach
    void createTwoCatalogues() {
        user = insertUser();
        sourceId = insertSource(user);
        actionId = insertCategory(sourceId, "Action");
        first = insertFilm(sourceId, "Le Voyage", actionId, 0);
        second = insertFilm(sourceId, "La Traversée", actionId, 1);
        // A channel on the same source: the two listings must not see each other.
        insertChannel(sourceId, "Chaîne 01");

        UserRow stranger = insertUser();
        strangerFilmId = insertFilm(insertSource(stranger), "Le Voyage", null, 0);
    }

    @Test
    @DisplayName("a page of films holds films, and none of the source's channels")
    void listsFilmsOnly() {
        List<VodItem> films = catalog.findVod(sourceId, user.id(), null, null, null, 0, 50);

        assertThat(films).extracting(VodItem::getId).containsExactly(first, second);
        assertThat(catalog.countVod(sourceId, user.id(), null, null, null)).isEqualTo(2);
    }

    @Test
    @DisplayName("a page of films carries neither a stream URL nor a synopsis")
    void theListingProjectionIsNarrow() {
        VodItem film = catalog.findVod(sourceId, user.id(), null, null, null, 0, 50).getFirst();

        // `plot` is fetched when somebody opens a film, not when a thousand scroll
        // past: on an Xtream panel it is one HTTP call per film, to the user's own
        // server. It is stored here and still absent from the page.
        assertThat(film.getPlot()).isNull();
        // And there is no accessor for a stream URL at all — the contract does not
        // carry one on `VodItem`, which is the strongest form this rule can take.
        assertThat(film.getPosterUrl()).isEqualTo("https://poster.example/1.jpg");
        assertThat(film.getYear()).isEqualTo(1998);
    }

    @Test
    @DisplayName("the search and the category filters compose, as on channels")
    void filtersCompose() {
        assertThat(catalog.findVod(sourceId, user.id(), actionId, "travers", null, 0, 50))
                .extracting(VodItem::getId).containsExactly(second);
        // Accent-insensitive it is not, and does not claim to be: ILIKE on a
        // substring, exactly what the channel search does.
        assertThat(catalog.findVod(sourceId, user.id(), null, "voyage", null, 0, 50))
                .extracting(VodItem::getId).containsExactly(first);
    }

    @Test
    @DisplayName("ids resolves exactly what was asked for, and ignores another account's")
    void idsIsScopedToTheOwner() {
        List<VodItem> found =
                catalog.findVod(sourceId, user.id(), null, null, List.of(second, strangerFilmId), 0, 50);

        // The stranger's film is absent rather than an error — the same rule the
        // channel lookup follows, so a caller cannot probe for identifiers.
        assertThat(found).extracting(VodItem::getId).containsExactly(second);
    }

    @Test
    @DisplayName("a film of another account has no playback URL to give")
    void playbackIsScopedToTheOwner() {
        assertThat(catalog.findVodStreamUrlOwnedBy(first, user.id())).isPresent();
        assertThat(catalog.findVodStreamUrlOwnedBy(strangerFilmId, user.id())).isEmpty();
        // Indistinguishable, on purpose, from an identifier that does not exist.
        assertThat(catalog.findVodStreamUrlOwnedBy(UUID.randomUUID(), user.id())).isEmpty();
    }

    // ---- helpers ------------------------------------------------------------

    private UserRow insertUser() {
        return users.insert(UUID.randomUUID(),
                "vod-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Vod Test", "en");
    }

    private UUID insertSource(UserRow owner) {
        UUID id = UUID.randomUUID();
        sources.insert(id, owner.id(), "Test source", SourceKind.M3U_URL, null, null, null,
                "https://playlist.example/one.m3u", null, null, null);
        return id;
    }

    private UUID insertCategory(UUID source, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO category (id, source_id, external_id, name, content_type, position)
                VALUES (:id, :sourceId, :externalId, :name, 'VOD', 0)
                """)
                .param("id", id)
                .param("sourceId", source)
                .param("externalId", "test:" + id)
                .param("name", name)
                .update();
        return id;
    }

    /**
     * Inserts a film directly.
     *
     * <p>Going through ingestion would mean an outbound HTTP request to somebody's
     * panel, which a test suite has no business making. What is under test here is
     * what happens to a film once it exists.
     */
    private UUID insertFilm(UUID source, String name, UUID categoryId, int position) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO vod_item (id, source_id, category_id, external_id, name,
                                      poster_url, year, plot, stream_url,
                                      container_extension, position)
                VALUES (:id, :sourceId, :categoryId, :externalId, :name,
                        'https://poster.example/1.jpg', 1998, 'Un synopsis.',
                        'https://stream.example/movie.mkv', 'mkv', :position)
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

    private void insertChannel(UUID source, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO channel (id, source_id, external_id, name, stream_url, position)
                VALUES (:id, :sourceId, :externalId, :name, 'https://stream.example/x.m3u8', 0)
                """)
                .param("id", id)
                .param("sourceId", source)
                .param("externalId", "test:" + id)
                .param("name", name)
                .update();
    }
}
