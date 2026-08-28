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
import tv.lumo.api.generated.model.UpdateFavoriteGroupRequest;
import tv.lumo.api.generated.model.UpdateFavoriteRequest;
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

    // ---- groups: rename, move, delete ---------------------------------------

    @Test
    @DisplayName("renaming the default group does not produce a second one on the next add")
    void renamingTheDefaultGroupKeepsItDefault() {
        Favorite first = userdata.addFavorite(user.id(), new AddFavoriteRequest(channelId));
        UUID defaultGroupId = first.getGroupId();

        userdata.updateGroup(user.id(), defaultGroupId,
                new UpdateFavoriteGroupRequest().name("Mes chaînes"));

        // The bug this flag exists for: looked up by name, the next add finds
        // nothing called "Favorites" and creates a second default group.
        Favorite second = userdata.addFavorite(user.id(), new AddFavoriteRequest(otherChannelId));

        assertThat(second.getGroupId()).isEqualTo(defaultGroupId);
        assertThat(userdata.listGroups(user.id())).hasSize(1);
        assertThat(userdata.listGroups(user.id()).getFirst().getName()).isEqualTo("Mes chaînes");
        assertThat(userdata.listGroups(user.id()).getFirst().getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("renaming onto a name another group already carries is a conflict")
    void renamingOntoATakenNameIsAConflict() {
        userdata.createGroup(user.id(), new CreateFavoriteGroupRequest("Documentaire"));
        FavoriteGroup cinema = userdata.createGroup(user.id(), new CreateFavoriteGroupRequest("Ciné"));

        assertThatThrownBy(() -> userdata.updateGroup(user.id(), cinema.getId(),
                new UpdateFavoriteGroupRequest().name("Documentaire")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.FAVORITE_GROUP_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("moving a group leaves the list contiguous from zero")
    void movingAGroupRenumbersTheList() {
        userdata.createGroup(user.id(), new CreateFavoriteGroupRequest("Documentaire"));
        userdata.createGroup(user.id(), new CreateFavoriteGroupRequest("Ciné"));
        FavoriteGroup sport = userdata.createGroup(user.id(), new CreateFavoriteGroupRequest("Sport"));

        userdata.updateGroup(user.id(), sport.getId(), new UpdateFavoriteGroupRequest().position(0));

        List<FavoriteGroup> groups = userdata.listGroups(user.id());
        assertThat(groups).extracting(FavoriteGroup::getName)
                .containsExactly("Sport", "Documentaire", "Ciné");
        assertThat(groups).extracting(FavoriteGroup::getPosition).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("deleting a group keeps its favourites: they move to the default group")
    void deletingAGroupKeepsItsFavorites() {
        Favorite kept = userdata.addFavorite(user.id(), new AddFavoriteRequest(channelId));
        FavoriteGroup docs = userdata.createGroup(user.id(),
                new CreateFavoriteGroupRequest("Documentaire"));
        userdata.addFavorite(user.id(),
                new AddFavoriteRequest(otherChannelId).groupId(docs.getId()));

        userdata.deleteGroup(user.id(), docs.getId());

        assertThat(userdata.listGroups(user.id())).hasSize(1);
        List<Favorite> remaining = userdata.listFavorites(user.id(), kept.getGroupId());
        assertThat(remaining).extracting(Favorite::getChannelId)
                .containsExactly(channelId, otherChannelId);
        // Appended after what was already there, and contiguous.
        assertThat(remaining).extracting(Favorite::getPosition).containsExactly(0, 1);
    }

    @Test
    @DisplayName("a favourite already in the default group is not duplicated by the move")
    void deletingAGroupDropsFavoritesAlreadyInTheDefault() {
        Favorite kept = userdata.addFavorite(user.id(), new AddFavoriteRequest(channelId));
        FavoriteGroup docs = userdata.createGroup(user.id(),
                new CreateFavoriteGroupRequest("Documentaire"));
        // The same channel, starred in both groups — which the model allows.
        userdata.addFavorite(user.id(), new AddFavoriteRequest(channelId).groupId(docs.getId()));

        userdata.deleteGroup(user.id(), docs.getId());

        List<Favorite> remaining = userdata.listFavorites(user.id(), kept.getGroupId());
        assertThat(remaining).extracting(Favorite::getChannelId).containsExactly(channelId);
    }

    @Test
    @DisplayName("the default group cannot be deleted: it is where the others empty into")
    void theDefaultGroupCannotBeDeleted() {
        Favorite favorite = userdata.addFavorite(user.id(), new AddFavoriteRequest(channelId));

        assertThatThrownBy(() -> userdata.deleteGroup(user.id(), favorite.getGroupId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.FAVORITE_GROUP_NOT_DELETABLE);
    }

    @Test
    @DisplayName("deleting somebody else's group is a 404, not a 403")
    void deletingSomebodyElsesGroupIsNotFound() {
        FavoriteGroup group = userdata.createGroup(user.id(), new CreateFavoriteGroupRequest("Sport"));
        UserRow stranger = users.insert(UUID.randomUUID(),
                "stranger-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Stranger", "en");

        assertThatThrownBy(() -> userdata.deleteGroup(stranger.id(), group.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.FAVORITE_GROUP_NOT_FOUND);

        assertThat(userdata.listGroups(user.id())).hasSize(1);
    }

    // ---- moving a favourite -------------------------------------------------

    @Test
    @DisplayName("moving a favourite between groups leaves one row, not two")
    void movingAFavoriteBetweenGroupsLeavesOneRow() {
        Favorite favorite = userdata.addFavorite(user.id(), new AddFavoriteRequest(channelId));
        UUID defaultGroupId = favorite.getGroupId();
        userdata.addFavorite(user.id(), new AddFavoriteRequest(otherChannelId));
        FavoriteGroup docs = userdata.createGroup(user.id(),
                new CreateFavoriteGroupRequest("Documentaire"));

        Favorite moved = userdata.updateFavorite(user.id(), favorite.getId(),
                new UpdateFavoriteRequest().groupId(docs.getId()));

        assertThat(moved.getId()).isEqualTo(favorite.getId());
        assertThat(moved.getGroupId()).isEqualTo(docs.getId());
        assertThat(userdata.listFavorites(user.id(), docs.getId())).hasSize(1);
        // The group it left is renumbered, so the survivor is back at zero.
        assertThat(userdata.listFavorites(user.id(), defaultGroupId))
                .extracting(Favorite::getPosition).containsExactly(0);
    }

    @Test
    @DisplayName("moving a favourite onto a group that already has the channel is a conflict")
    void movingOntoADuplicateIsAConflict() {
        Favorite favorite = userdata.addFavorite(user.id(), new AddFavoriteRequest(channelId));
        FavoriteGroup docs = userdata.createGroup(user.id(),
                new CreateFavoriteGroupRequest("Documentaire"));
        userdata.addFavorite(user.id(), new AddFavoriteRequest(channelId).groupId(docs.getId()));

        assertThatThrownBy(() -> userdata.updateFavorite(user.id(), favorite.getId(),
                new UpdateFavoriteRequest().groupId(docs.getId())))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.FAVORITE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("reordering within a group leaves the positions contiguous from zero")
    void reorderingWithinAGroupIsContiguous() {
        UUID thirdChannelId = insertChannel(sourceId, "Chaîne 03");
        userdata.addFavorite(user.id(), new AddFavoriteRequest(channelId));
        userdata.addFavorite(user.id(), new AddFavoriteRequest(otherChannelId));
        Favorite third = userdata.addFavorite(user.id(), new AddFavoriteRequest(thirdChannelId));

        userdata.updateFavorite(user.id(), third.getId(), new UpdateFavoriteRequest().position(0));

        List<Favorite> favorites = userdata.listFavorites(user.id(), third.getGroupId());
        assertThat(favorites).extracting(Favorite::getChannelId)
                .containsExactly(thirdChannelId, channelId, otherChannelId);
        assertThat(favorites).extracting(Favorite::getPosition).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("moving a favourite into somebody else's group is a 404, not a 403")
    void movingIntoSomebodyElsesGroupIsNotFound() {
        Favorite favorite = userdata.addFavorite(user.id(), new AddFavoriteRequest(channelId));
        UserRow stranger = users.insert(UUID.randomUUID(),
                "stranger-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Stranger", "en");
        FavoriteGroup theirs = userdata.createGroup(stranger.id(),
                new CreateFavoriteGroupRequest("Leur groupe"));

        // A 403 here would confirm that the group exists.
        assertThatThrownBy(() -> userdata.updateFavorite(user.id(), favorite.getId(),
                new UpdateFavoriteRequest().groupId(theirs.getId())))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.FAVORITE_GROUP_NOT_FOUND);
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

        PlaybackProgressPage page = userdata.listProgress(user.id(), null, null, null, 0, 50);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("progress is read back most recently updated first, and filters to one item")
    void progressReadsBackInRailOrder() {
        userdata.saveProgress(user.id(), progressRequest("vod:1", 10L, null));
        userdata.saveProgress(user.id(), progressRequest("vod:2", 20L, null));

        PlaybackProgressPage page = userdata.listProgress(user.id(), null, null, null, 0, 50);
        assertThat(page.getItems()).extracting(PlaybackProgress::getItemRef)
                // The order a "Continue watching" rail wants, so no client re-sorts it.
                .containsExactly("vod:2", "vod:1");

        PlaybackProgressPage one = userdata.listProgress(user.id(), null, ProgressItemType.VOD, "vod:1", 0, 50);
        assertThat(one.getItems()).hasSize(1);
        assertThat(one.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("an item_ref containing a slash round-trips, which is why it is a query parameter")
    void itemRefIsOpaque() {
        // Minted by the user's own panel. Nothing here parses it, and it never
        // becomes a path segment.
        userdata.saveProgress(user.id(), progressRequest("series/12/ep 3?x=1", 5L, null));

        PlaybackProgressPage page = userdata.listProgress(user.id(), null,
                ProgressItemType.VOD, "series/12/ep 3?x=1", 0, 50);
        assertThat(page.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("two sources can use the same item_ref without sharing a position")
    void progressIsKeyedOnTheSourceToo() {
        UUID otherSourceId = UUID.randomUUID();
        sources.insert(otherSourceId, user.id(), "Second source", SourceKind.M3U_URL, null, null,
                null, "https://playlist.example/two.m3u", null, null, null);

        // `item_ref` is minted by the user's own panel. Two subscriptions using
        // `1042` for two different films is ordinary, not adversarial — and before
        // the source joined the key, the second save moved the first one's row.
        userdata.saveProgress(user.id(), progressRequest(sourceId, "1042", 1_000L, null));
        userdata.saveProgress(user.id(), progressRequest(otherSourceId, "1042", 45_000L, null));

        PlaybackProgressPage all = userdata.listProgress(user.id(), null, null, null, 0, 50);
        assertThat(all.getTotalElements()).isEqualTo(2);

        // And each one reads back its own position. A film resuming twenty minutes
        // in on its first viewing is what the collision looked like from outside.
        PlaybackProgressPage first =
                userdata.listProgress(user.id(), sourceId, ProgressItemType.VOD, "1042", 0, 50);
        assertThat(first.getItems()).hasSize(1);
        assertThat(first.getItems().getFirst().getPositionMs()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("progress cannot be filed under a source the caller does not own")
    void progressChecksSourceOwnership() {
        UserRow stranger = users.insert(UUID.randomUUID(),
                "stranger-" + UUID.randomUUID() + "@test.example",
                "$argon2id$irrelevant", "Stranger", "en");
        UUID theirSource = UUID.randomUUID();
        sources.insert(theirSource, stranger.id(), "Theirs", SourceKind.M3U_URL, null, null, null,
                "https://playlist.example/theirs.m3u", null, null, null);

        // A 404 and not a 403: confirming the source exists would let this endpoint
        // be used to discover identifiers.
        assertThatThrownBy(() -> userdata.saveProgress(user.id(),
                progressRequest(theirSource, "1042", 1_000L, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.SOURCE_NOT_FOUND);
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

    private SaveProgressRequest progressRequest(String itemRef, long positionMs, Long durationMs) {
        return progressRequest(sourceId, itemRef, positionMs, durationMs);
    }

    /** The source is part of the key, so a test that means to collide has to say so. */
    private static SaveProgressRequest progressRequest(UUID source, String itemRef,
                                                       long positionMs, Long durationMs) {
        SaveProgressRequest request =
                new SaveProgressRequest(source, ProgressItemType.VOD, itemRef, positionMs);
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
