package tv.lumo.api.userdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import tv.lumo.api.generated.model.AddFavoriteRequest;
import tv.lumo.api.generated.model.CreateFavoriteGroupRequest;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.Favorite;
import tv.lumo.api.generated.model.FavoriteGroup;
import tv.lumo.api.generated.model.PlaybackProgress;
import tv.lumo.api.generated.model.PlaybackProgressPage;
import tv.lumo.api.generated.model.ProgressItemType;
import tv.lumo.api.generated.model.RecentChannel;
import tv.lumo.api.generated.model.SaveProgressRequest;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.shared.error.ApiException;
import tv.lumo.api.source.SourceRepository;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * Favourites, progress and recently watched channels, against a real database.
 *
 * <p>Three things here are the database's behaviour rather than Java's, which is
 * why this is an integration test and not a unit one: the two unique indexes that
 * turn "already there" into an answer instead of a duplicate row, the two
 * {@code ON CONFLICT} upserts, and the multi-tenant filter that makes another
 * user's channel a 404 rather than a leak.
 */
@Import(PostgresContainerInitializer.class)
class UserdataIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private UserdataService userdata;

    @Autowired
    private UserRepository users;

    @Autowired
    private SourceRepository sources;

    @Autowired
    private JdbcClient jdbc;

    private UserRow user;
    private UUID sourceId;
    private UUID channelId;
    private UUID otherChannelId;

    @BeforeEach
    void createCatalogue() {
        user = users.insert(UUID.randomUUID(),
                "userdata-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Userdata Test", "en");

        sourceId = UUID.randomUUID();
        sources.insert(sourceId, user.id(), "Test source", SourceKind.M3U_URL, null, null, null,
                "https://playlist.example/one.m3u", null, null, null);
        channelId = insertChannel(sourceId, "Chaîne 01");
        otherChannelId = insertChannel(sourceId, "Chaîne 02");
    }

    // ---- favourites ---------------------------------------------------------

    @Test
    @DisplayName("the first favourite creates the default group and derives its source")
    void firstFavoriteCreatesTheDefaultGroup() {
        Favorite favorite = userdata.addFavorite(user.id(), new AddFavoriteRequest(channelId));

        assertThat(favorite.getChannelId()).isEqualTo(channelId);
        // Derived from the channel, never accepted from the request, so the two
        // cannot disagree.
        assertThat(favorite.getSourceId()).isEqualTo(sourceId);
        assertThat(favorite.getPosition()).isZero();

        List<FavoriteGroup> groups = userdata.listGroups(user.id());
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().getId()).isEqualTo(favorite.getGroupId());
    }

    @Test
    @DisplayName("the same channel twice in one group is a conflict, not a second row")
    void duplicateFavoriteIsAConflict() {
        userdata.addFavorite(user.id(), new AddFavoriteRequest(channelId));

        assertThatThrownBy(() -> userdata.addFavorite(user.id(), new AddFavoriteRequest(channelId)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.FAVORITE_ALREADY_EXISTS);

        assertThat(userdata.listFavorites(user.id(), null)).hasSize(1);
    }

    @Test
    @DisplayName("favouriting someone else's channel is a 404, not a 403")
    void anotherUsersChannelIsNotFound() {
        UserRow stranger = users.insert(UUID.randomUUID(),
                "stranger-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Stranger", "en");

        assertThatThrownBy(() -> userdata.addFavorite(stranger.id(), new AddFavoriteRequest(channelId)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                // 403 would confirm the id exists. These endpoints must not be
                // usable to discover channel ids.
                .isEqualTo(ErrorCode.CHANNEL_NOT_FOUND);
    }

    @Test
    @DisplayName("a favourite lands in a named group, and is appended last")
    void favoritesAppendWithinTheirGroup() {
        FavoriteGroup group = userdata.createGroup(user.id(), new CreateFavoriteGroupRequest("Sport"));

        userdata.addFavorite(user.id(), new AddFavoriteRequest(channelId).groupId(group.getId()));
        Favorite second = userdata.addFavorite(user.id(),
                new AddFavoriteRequest(otherChannelId).groupId(group.getId()));

        assertThat(second.getPosition()).isEqualTo(1);
        assertThat(userdata.listFavorites(user.id(), group.getId())).hasSize(2);
    }

    @Test
    @DisplayName("two groups cannot share a name")
    void groupNamesAreUnique() {
        userdata.createGroup(user.id(), new CreateFavoriteGroupRequest("Sport"));

        assertThatThrownBy(() -> userdata.createGroup(user.id(), new CreateFavoriteGroupRequest("Sport")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.FAVORITE_GROUP_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("removing a favourite that is not yours is a 404")
    void removingSomebodyElsesFavoriteIsNotFound() {
        Favorite favorite = userdata.addFavorite(user.id(), new AddFavoriteRequest(channelId));
        UserRow stranger = users.insert(UUID.randomUUID(),
                "stranger-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Stranger", "en");

        assertThatThrownBy(() -> userdata.removeFavorite(stranger.id(), favorite.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.FAVORITE_NOT_FOUND);

        assertThat(userdata.listFavorites(user.id(), null)).hasSize(1);
    }

    // ---- progress -----------------------------------------------------------

    @Test
    @DisplayName("saving progress twice for one item moves it rather than adding a row")
    void progressIsAnUpsert() {
        PlaybackProgress first = userdata.saveProgress(user.id(),
                progressRequest("vod:42", 1_000L, 90_000L));
        PlaybackProgress second = userdata.saveProgress(user.id(),
                progressRequest("vod:42", 45_000L, 90_000L));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getPositionMs()).isEqualTo(45_000L);

        PlaybackProgressPage page = userdata.listProgress(user.id(), null, null, 0, 50);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("progress is read back most recently updated first, and filters to one item")
    void progressReadsBackInRailOrder() {
        userdata.saveProgress(user.id(), progressRequest("vod:1", 10L, null));
        userdata.saveProgress(user.id(), progressRequest("vod:2", 20L, null));

        PlaybackProgressPage page = userdata.listProgress(user.id(), null, null, 0, 50);
        assertThat(page.getItems()).extracting(PlaybackProgress::getItemRef)
                // The order a "Continue watching" rail wants, so no client re-sorts it.
                .containsExactly("vod:2", "vod:1");

        PlaybackProgressPage one = userdata.listProgress(user.id(), ProgressItemType.VOD, "vod:1", 0, 50);
        assertThat(one.getItems()).hasSize(1);
        assertThat(one.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("an item_ref containing a slash round-trips, which is why it is a query parameter")
    void itemRefIsOpaque() {
        // Minted by the user's own panel. Nothing here parses it, and it never
        // becomes a path segment.
        userdata.saveProgress(user.id(), progressRequest("series/12/ep 3?x=1", 5L, null));

        PlaybackProgressPage page = userdata.listProgress(user.id(),
                ProgressItemType.VOD, "series/12/ep 3?x=1", 0, 50);
        assertThat(page.getItems()).hasSize(1);
    }

    // ---- recently watched ---------------------------------------------------

    @Test
    @DisplayName("watching a channel again moves it to the top instead of adding a row")
    void recentChannelsAreAnUpsert() {
        userdata.recordRecentChannel(user.id(), channelId);
        userdata.recordRecentChannel(user.id(), otherChannelId);
        userdata.recordRecentChannel(user.id(), channelId);

        List<RecentChannel> recent = userdata.listRecentChannels(user.id(), 20);

        assertThat(recent).hasSize(2);
        assertThat(recent.getFirst().getChannelId()).isEqualTo(channelId);
        assertThat(recent.getFirst().getSourceId()).isEqualTo(sourceId);
    }

    @Test
    @DisplayName("recording someone else's channel is a 404")
    void recordingAnotherUsersChannelIsNotFound() {
        UserRow stranger = users.insert(UUID.randomUUID(),
                "stranger-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Stranger", "en");

        assertThatThrownBy(() -> userdata.recordRecentChannel(stranger.id(), channelId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.CHANNEL_NOT_FOUND);
    }

    @Test
    @DisplayName("the window is rolling: the oldest entries are dropped silently")
    void recentChannelsAreAWindow() {
        // Fifty-one channels watched once each. This feeds a rail, not a history,
        // and the fifty-first must push the first out rather than accumulate.
        for (int i = 0; i < 51; i++) {
            userdata.recordRecentChannel(user.id(), insertChannel(sourceId, "Chaîne %02d".formatted(i)));
        }

        Long stored = jdbc.sql("SELECT count(*) FROM recent_channel WHERE user_id = :id")
                .param("id", user.id())
                .query(Long.class)
                .single();
        assertThat(stored).isEqualTo(50);
    }

    // ---- helpers ------------------------------------------------------------

    private static SaveProgressRequest progressRequest(String itemRef, long positionMs, Long durationMs) {
        SaveProgressRequest request = new SaveProgressRequest(ProgressItemType.VOD, itemRef, positionMs);
        request.setDurationMs(durationMs);
        return request;
    }

    /**
     * Inserts a channel directly.
     *
     * <p>Going through ingestion would mean an outbound HTTP request to somebody's
     * playlist, which a test suite has no business making. What is under test here
     * is what happens to a channel once it exists.
     */
    private UUID insertChannel(UUID source, String name) {
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
        return id;
    }
}
