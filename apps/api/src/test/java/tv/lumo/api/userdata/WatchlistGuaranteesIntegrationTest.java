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
import tv.lumo.api.auth.DeviceRepository;
import tv.lumo.api.auth.SessionService;
import tv.lumo.api.auth.UserRepository;
import tv.lumo.api.generated.model.AddFavoriteRequest;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.Favorite;
import tv.lumo.api.generated.model.ProgressItemType;
import tv.lumo.api.generated.model.SaveProgressRequest;
import tv.lumo.api.generated.model.UpdateFavoriteRequest;
import tv.lumo.api.shared.error.ApiException;
import tv.lumo.api.source.SourceRepository;
import tv.lumo.api.support.MultiSourceAccountsFixture;
import tv.lumo.api.support.PostgresContainerInitializer;
import tv.lumo.api.support.PostgresIntegrationTest;

/**
 * G1, G2, G3 and G5 against the real database and the real service.
 *
 * <p>{@link VisibleFavoriteOrderTest} proves the translation is pure; this class
 * proves the translation is <em>usable</em>: that the order the pure function
 * predicts is the order the contract's single-favourite moves actually produce,
 * that an interrupted sequence keeps what succeeded, that the server's acceptance
 * order decides, and that watching something never removes it.
 *
 * <p>The fixture is the shared two-account, multi-source one
 * ({@link MultiSourceAccountsFixture}), so these tests and QA's isolation suite
 * start from the same world.
 */
@Import(PostgresContainerInitializer.class)
class WatchlistGuaranteesIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private UserdataService userdata;

    @Autowired
    private UserRepository users;

    @Autowired
    private SourceRepository sources;

    @Autowired
    private DeviceRepository devices;

    @Autowired
    private SessionService sessions;

    @Autowired
    private JdbcClient jdbc;

    private MultiSourceAccountsFixture.Dataset data;
    private UUID userId;
    private UUID groupId;

    @BeforeEach
    void seed() {
        data = new MultiSourceAccountsFixture(users, sources, devices, sessions, userdata, jdbc).seed();
        userId = data.a().user().id();
        groupId = data.namedGroupId();
    }

    @Test
    @DisplayName("G1+G2: the visible permutation is reachable as single moves, and leaves the other source alone")
    void theFilteredPermutationIsReachableBySingleMoves() {
        List<Favorite> before = favorites();
        // A1, B1, A2, B2, A3 — A* belongs to the active source.
        List<Favorite> target = VisibleFavoriteOrder.applyVisibleMove(before, data::belongsToA1, 2, 0);

        // No batch endpoint exists: the client realises the move one favourite at a
        // time. Each call is one intention, and none is atomic with the next.
        for (int position = 0; position < target.size(); position++) {
            userdata.updateFavorite(userId, target.get(position).getId(),
                    new UpdateFavoriteRequest().position(position));
        }

        List<Favorite> after = favorites();
        assertThat(after).extracting(Favorite::getId)
                .containsExactlyElementsOf(target.stream().map(Favorite::getId).toList());
        // The other source did not slide: B1 and B2 are still at 1 and 3.
        assertThat(after.get(1).getSourceId()).isEqualTo(data.sourceA2());
        assertThat(after.get(3).getSourceId()).isEqualTo(data.sourceA2());
    }

    @Test
    @DisplayName("G2: a move that fails in the middle keeps everything that already succeeded")
    void aFailureInTheMiddleKeepsWhatSucceeded() {
        List<Favorite> before = favorites();
        Favorite movedToFront = before.get(4);

        // First intention: accepted.
        userdata.updateFavorite(userId, movedToFront.getId(), new UpdateFavoriteRequest().position(0));

        // Second intention: a favourite that belongs to account B. 404, never a 403.
        UUID foreignFavorite = userdata
                .addFavorite(data.b().user().id(), new AddFavoriteRequest(data.channelB1()))
                .getId();
        assertThatThrownBy(() -> userdata.updateFavorite(userId, foreignFavorite,
                new UpdateFavoriteRequest().position(1)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.FAVORITE_NOT_FOUND);

        // The successful move is conserved, the group is still contiguous, and the
        // failure was reported rather than swallowed as a global success.
        List<Favorite> after = favorites();
        assertThat(after.getFirst().getId()).isEqualTo(movedToFront.getId());
        assertThat(after).extracting(Favorite::getPosition).containsExactly(0, 1, 2, 3, 4);
        assertThat(after).extracting(Favorite::getId).containsExactlyInAnyOrderElementsOf(
                before.stream().map(Favorite::getId).toList());
    }

    @Test
    @DisplayName("G3: of two accepted intentions on one favourite, the last accepted decides")
    void theLastAcceptedIntentionWins() {
        Favorite favourite = favorites().getFirst();

        userdata.updateFavorite(userId, favourite.getId(), new UpdateFavoriteRequest().position(3));
        userdata.updateFavorite(userId, favourite.getId(), new UpdateFavoriteRequest().position(1));

        List<Favorite> after = favorites();
        Favorite reread = after.stream()
                .filter(f -> f.getId().equals(favourite.getId()))
                .findFirst()
                .orElseThrow();
        // The server has no client clock to consult: the second PATCH was accepted
        // after the first, so its position is the one that stands.
        assertThat(reread.getPosition()).isEqualTo(1);
        assertThat(after.get(1).getId()).isEqualTo(favourite.getId());
        assertThat(after).extracting(Favorite::getPosition).containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    @DisplayName("G5: watching a favourite changes neither its membership nor its rank")
    void watchingDoesNotRemoveAFavourite() {
        List<Favorite> before = favorites();
        Favorite watched = before.getFirst();

        userdata.recordRecentChannel(userId, watched.getChannelId());
        userdata.saveProgress(userId,
                new SaveProgressRequest(data.sourceA1(), ProgressItemType.VOD, "vod:42", 1_000L));

        List<Favorite> after = favorites();
        assertThat(after).extracting(Favorite::getId)
                .containsExactlyElementsOf(before.stream().map(Favorite::getId).toList());
        assertThat(after).extracting(Favorite::getPosition).containsExactly(0, 1, 2, 3, 4);
    }

    private List<Favorite> favorites() {
        return userdata.listFavorites(userId, groupId);
    }
}
