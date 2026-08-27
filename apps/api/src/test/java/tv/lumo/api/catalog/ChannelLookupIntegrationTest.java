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
import tv.lumo.api.generated.model.Channel;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.source.SourceRepository;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * The {@code ids} filter on {@code GET /sources/{id}/channels}.
 *
 * <p>It exists so that a client holding identifiers and nothing else — a
 * favourites rail, a recently watched rail — can turn them into rows a person can
 * read. {@code Favorite} and {@code RecentChannel} carry no name on purpose, and
 * a client without a local catalogue (the web) has no other way to resolve them.
 *
 * <p>An integration test rather than a unit one, because everything worth
 * asserting is the database's behaviour: the {@code uuid[]} the filter is turned
 * into, the ownership join that decides what an identifier from another account
 * resolves to, and the composition with the two filters that were already there.
 */
@Import(PostgresContainerInitializer.class)
class ChannelLookupIntegrationTest extends PostgresIntegrationTest {

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
    private UUID sportId;
    private UUID first;
    private UUID second;
    private UUID third;
    private UUID strangerChannelId;

    @BeforeEach
    void createTwoCatalogues() {
        user = insertUser();
        sourceId = insertSource(user);
        sportId = insertCategory(sourceId, "Sport");
        first = insertChannel(sourceId, "Chaine 01", sportId, 0);
        second = insertChannel(sourceId, "Chaine 02", sportId, 1);
        third = insertChannel(sourceId, "Chaine 03", null, 2);

        UserRow stranger = insertUser();
        strangerChannelId = insertChannel(insertSource(stranger), "Chaine 01", null, 0);
    }

    @Test
    @DisplayName("resolves exactly the channels asked for, and counts them")
    void resolvesTheChannelsAskedFor() {
        List<Channel> found = find(List.of(third, first));

        assertThat(found).extracting(Channel::getId).containsExactlyInAnyOrder(first, third);
        assertThat(found).extracting(Channel::getName)
                .containsExactlyInAnyOrder("Chaine 01", "Chaine 03");
        assertThat(catalog.countChannels(sourceId, user.id(), null, null, List.of(third, first)))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("the order stays the catalogue's, not the order the ids came in")
    void keepsTheCatalogueOrder() {
        // The caller holds the order it wants — a favourite's position, a watch
        // date — and sorts by it. Honouring the order of the parameter instead
        // would give the same operation two different orderings depending on
        // which filter was used, and a rail that silently reorders itself the day
        // a channel drops out of the playlist.
        assertThat(find(List.of(third, second, first)))
                .extracting(Channel::getName)
                .containsExactly("Chaine 01", "Chaine 02", "Chaine 03");
    }

    @Test
    @DisplayName("an id that is not this user's is absent, not an error and not a leak")
    void ignoresIdentifiersThatAreNotTheirs() {
        // A stale favourite pointing at a channel the last re-synchronisation
        // dropped, and a channel belonging to somebody else, get the same answer:
        // nothing. Anything louder would turn a stale rail into a broken screen —
        // and a 404 would let a caller test whether a channel id exists.
        List<UUID> mixed = List.of(first, strangerChannelId, UUID.randomUUID());

        assertThat(find(mixed)).extracting(Channel::getId).containsExactly(first);
        assertThat(catalog.countChannels(sourceId, user.id(), null, null, mixed)).isEqualTo(1);
    }

    @Test
    @DisplayName("composes with the category and the search rather than replacing them")
    void composesWithTheOtherFilters() {
        assertThat(catalog.findChannels(sourceId, user.id(), sportId, null,
                List.of(first, third), 0, 50))
                .extracting(Channel::getId)
                .containsExactly(first);

        assertThat(catalog.findChannels(sourceId, user.id(), null, "02",
                List.of(first, second), 0, 50))
                .extracting(Channel::getId)
                .containsExactly(second);
    }

    @Test
    @DisplayName("no ids at all leaves the catalogue whole")
    void noFilterReturnsEverything() {
        // Null and empty both mean "no filter". The alternative — an empty list
        // restricting to nothing — would answer a stray `?ids=` with a catalogue
        // that looks like it lost its channels.
        assertThat(find(null)).hasSize(3);
        assertThat(find(List.of())).hasSize(3);
    }

    // ---- helpers ------------------------------------------------------------

    private List<Channel> find(List<UUID> ids) {
        return catalog.findChannels(sourceId, user.id(), null, null, ids, 0, 50);
    }

    private UserRow insertUser() {
        return users.insert(UUID.randomUUID(),
                "catalog-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Catalog Test", "en");
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
                VALUES (:id, :sourceId, :externalId, :name, 'LIVE', 0)
                """)
                .param("id", id)
                .param("sourceId", source)
                .param("externalId", "test:" + id)
                .param("name", name)
                .update();
        return id;
    }

    /**
     * Inserts a channel directly.
     *
     * <p>Going through ingestion would mean an outbound HTTP request to somebody's
     * playlist, which a test suite has no business making. What is under test here
     * is what happens to a channel once it exists.
     */
    private UUID insertChannel(UUID source, String name, UUID categoryId, int position) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO channel (id, source_id, category_id, external_id, name, stream_url, position)
                VALUES (:id, :sourceId, :categoryId, :externalId, :name,
                        'https://stream.example/x.m3u8', :position)
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
}
