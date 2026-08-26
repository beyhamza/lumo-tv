package tv.lumo.api.userdata;

import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tv.lumo.api.catalog.CatalogReadRepository;
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
import tv.lumo.api.shared.error.ApiException;

/**
 * Favourites, favourite groups, playback progress and recently watched channels.
 *
 * <p>Two rules run through every method here.
 *
 * <p><b>Ownership is resolved, never accepted.</b> No request body carries a
 * {@code source_id}: it is looked up from the channel, scoped to the caller, so a
 * client cannot file someone else's channel under a source of its own. A channel
 * the caller does not own is reported as {@code CHANNEL_NOT_FOUND} rather than
 * {@code FORBIDDEN}, so these endpoints cannot be used to discover channel ids.
 *
 * <p><b>Uniqueness is the database's answer, not a prior SELECT.</b> Adding the
 * same channel to the same group twice is caught by the unique index rather than
 * by a check-then-insert, which is a race with two applications signed into one
 * account and a phone and a television both starring the same thing.
 */
@Service
public class UserdataService {

    /**
     * Name of the group created on the first add.
     *
     * <p>A <b>fallback label</b>, in the same sense as the M3U "Unclassified"
     * bucket: the server has no business authoring user-facing copy in one
     * language. Unlike that bucket, though, {@code FavoriteGroup} carries no
     * stable identifier a client could translate from — it has {@code id},
     * {@code name} and {@code position} and nothing else — so a client that wants
     * a French default has to rename it. That is a gap in the contract, recorded
     * in docs/design/api-gaps.md rather than papered over here.
     */
    static final String DEFAULT_GROUP_NAME = "Favorites";

    /**
     * How many recently watched channels an account keeps.
     *
     * <p>Equal to the largest page the contract lets a client ask for, and not one
     * row more: this feeds a rail, and everything beyond what can be displayed
     * would be a viewing history kept for no feature at all.
     */
    private static final int RECENT_WINDOW = 50;

    private static final int MAX_PAGE_SIZE = 200;

    private final FavoriteRepository favorites;
    private final ProgressRepository progress;
    private final RecentChannelRepository recents;
    private final CatalogReadRepository catalog;

    public UserdataService(FavoriteRepository favorites,
                           ProgressRepository progress,
                           RecentChannelRepository recents,
                           CatalogReadRepository catalog) {
        this.favorites = favorites;
        this.progress = progress;
        this.recents = recents;
        this.catalog = catalog;
    }

    // ---- favourites ---------------------------------------------------------

    public List<Favorite> listFavorites(UUID userId, UUID groupId) {
        return favorites.findFavorites(userId, groupId);
    }

    @Transactional
    public Favorite addFavorite(UUID userId, AddFavoriteRequest request) {
        UUID sourceId = catalog.findOwnedChannelSourceId(request.getChannelId(), userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.CHANNEL_NOT_FOUND,
                        "No such channel on a source owned by the caller"));

        UUID groupId = request.getGroupId() == null
                ? favorites.findOrCreateDefaultGroup(userId, DEFAULT_GROUP_NAME)
                : favorites.findOwnedGroupId(request.getGroupId(), userId)
                        .orElseThrow(() -> ApiException.notFound(ErrorCode.FAVORITE_GROUP_NOT_FOUND,
                                "No such favourite group on this account"));

        try {
            return favorites.insert(userId, groupId, sourceId, request.getChannelId(),
                    request.getPosition());
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict(ErrorCode.FAVORITE_ALREADY_EXISTS,
                    "This channel is already in that group");
        }
    }

    public void removeFavorite(UUID userId, UUID favoriteId) {
        if (favorites.delete(favoriteId, userId) == 0) {
            throw ApiException.notFound(ErrorCode.FAVORITE_NOT_FOUND,
                    "No such favourite on this account");
        }
    }

    public List<FavoriteGroup> listGroups(UUID userId) {
        return favorites.findGroups(userId);
    }

    public FavoriteGroup createGroup(UUID userId, CreateFavoriteGroupRequest request) {
        try {
            return favorites.insertGroup(userId, request.getName().trim(), request.getPosition());
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict(ErrorCode.FAVORITE_GROUP_ALREADY_EXISTS,
                    "A group with this name already exists");
        }
    }

    // ---- progress -----------------------------------------------------------

    public PlaybackProgressPage listProgress(UUID userId, ProgressItemType itemType, String itemRef,
                                             Integer page, Integer size) {
        int pageIndex = page == null ? 0 : Math.max(0, page);
        int pageSize = size == null ? 50 : Math.clamp(size, 1, MAX_PAGE_SIZE);
        String ref = (itemRef == null || itemRef.isBlank()) ? null : itemRef;

        List<PlaybackProgress> items = progress.findPage(userId, itemType, ref, pageIndex, pageSize);
        long total = progress.count(userId, itemType, ref);

        return new PlaybackProgressPage(items, pageIndex, pageSize, total,
                (int) Math.ceil((double) total / pageSize));
    }

    public PlaybackProgress saveProgress(UUID userId, SaveProgressRequest request) {
        return progress.upsert(userId, request.getItemType(), request.getItemRef(),
                request.getPositionMs(), request.getDurationMs());
    }

    // ---- recently watched ---------------------------------------------------

    public List<RecentChannel> listRecentChannels(UUID userId, Integer limit) {
        return recents.findRecent(userId, limit == null ? 20 : Math.clamp(limit, 1, RECENT_WINDOW));
    }

    /**
     * Records that playback started on a channel, and trims the window.
     *
     * <p>The prune runs on every write rather than on a timer: it deletes at most
     * one row in the steady state, and a sweep would leave the window wrong for
     * whatever interval it ran on.
     */
    @Transactional
    public RecentChannel recordRecentChannel(UUID userId, UUID channelId) {
        UUID sourceId = catalog.findOwnedChannelSourceId(channelId, userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.CHANNEL_NOT_FOUND,
                        "No such channel on a source owned by the caller"));

        RecentChannel recorded = recents.record(userId, sourceId, channelId);
        recents.prune(userId, RECENT_WINDOW);
        return recorded;
    }
}
