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
import tv.lumo.api.generated.model.UpdateFavoriteGroupRequest;
import tv.lumo.api.generated.model.UpdateFavoriteRequest;
import tv.lumo.api.shared.error.ApiException;
import tv.lumo.api.source.SourceRepository;

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
     * stable identifier a client could translate from. It carries
     * {@code is_default} now, which is exactly that identifier: a client renders
     * its own wording while the flag is true and the name is still this one, and
     * defers to the user's wording the moment they rename the group.
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
    private final SourceRepository sources;

    public UserdataService(FavoriteRepository favorites,
                           ProgressRepository progress,
                           RecentChannelRepository recents,
                           CatalogReadRepository catalog,
                           SourceRepository sources) {
        this.favorites = favorites;
        this.progress = progress;
        this.recents = recents;
        this.catalog = catalog;
        this.sources = sources;
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

    /**
     * Renames a group, moves it in the list, or both.
     *
     * <p>The default group is renamed like any other and keeps its flag: it stays
     * where an add without a {@code group_id} lands, and where a deleted group
     * empties into. That is the whole point of the flag being separate from the
     * name.
     */
    @Transactional
    public FavoriteGroup updateGroup(UUID userId, UUID groupId, UpdateFavoriteGroupRequest request) {
        FavoriteGroup current = favorites.findGroup(groupId, userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.FAVORITE_GROUP_NOT_FOUND,
                        "No such favourite group on this account"));

        String name = request.getName() == null ? null : request.getName().trim();
        Integer position = request.getPosition();

        if (position != null) {
            favorites.shiftGroupsFrom(userId, groupId, Math.max(0, position));
        }

        try {
            favorites.updateGroup(groupId, userId, name, position == null ? null : Math.max(0, position));
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict(ErrorCode.FAVORITE_GROUP_ALREADY_EXISTS,
                    "Another group already carries this name");
        }

        favorites.renumberGroups(userId);

        return favorites.findGroup(groupId, userId).orElse(current);
    }

    /**
     * Deletes a group and keeps its favourites, which move to the default group.
     *
     * <p>Deleting a shelf and throwing away the books are two different actions,
     * and the destructive reading is not the one this takes. The whole move is one
     * transaction: a group that disappears while its favourites are half-moved
     * would leave rows pointing at nothing.
     */
    @Transactional
    public void deleteGroup(UUID userId, UUID groupId) {
        FavoriteGroup group = favorites.findGroup(groupId, userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.FAVORITE_GROUP_NOT_FOUND,
                        "No such favourite group on this account"));

        if (Boolean.TRUE.equals(group.getIsDefault())) {
            throw ApiException.conflict(ErrorCode.FAVORITE_GROUP_NOT_DELETABLE,
                    "The default group is where the others empty into and cannot be deleted");
        }

        if (favorites.countFavorites(groupId) > 0) {
            UUID defaultGroupId = favorites.findOrCreateDefaultGroup(userId, DEFAULT_GROUP_NAME);
            favorites.deleteFavoritesAlreadyIn(groupId, defaultGroupId);
            favorites.moveFavoritesToGroup(groupId, defaultGroupId);
            favorites.renumberFavorites(defaultGroupId);
        }

        favorites.deleteGroup(groupId, userId);
        favorites.renumberGroups(userId);
    }

    /**
     * Moves a favourite to another group, reorders it within its own, or both.
     *
     * <p>A remove followed by an add would do the same thing on a good day. It
     * loses the position, and a connection dropped between the two calls loses the
     * favourite — so moving is one operation, in one transaction, because it is
     * one intention.
     */
    @Transactional
    public Favorite updateFavorite(UUID userId, UUID favoriteId, UpdateFavoriteRequest request) {
        Favorite current = favorites.findFavorite(favoriteId, userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.FAVORITE_NOT_FOUND,
                        "No such favourite on this account"));

        UUID targetGroupId = current.getGroupId();
        if (request.getGroupId() != null && !request.getGroupId().equals(targetGroupId)) {
            targetGroupId = favorites.findOwnedGroupId(request.getGroupId(), userId)
                    .orElseThrow(() -> ApiException.notFound(ErrorCode.FAVORITE_GROUP_NOT_FOUND,
                            "No such favourite group on this account"));
        }

        UUID sourceGroupId = current.getGroupId();
        boolean changesGroup = !targetGroupId.equals(sourceGroupId);

        int position = request.getPosition() == null
                ? (changesGroup ? favorites.countFavorites(targetGroupId) : current.getPosition())
                : Math.max(0, request.getPosition());

        favorites.shiftFavoritesFrom(targetGroupId, favoriteId, position);

        try {
            favorites.moveFavorite(favoriteId, targetGroupId, position);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict(ErrorCode.FAVORITE_ALREADY_EXISTS,
                    "This channel is already in that group");
        }

        favorites.renumberFavorites(targetGroupId);
        if (changesGroup) {
            favorites.renumberFavorites(sourceGroupId);
        }

        return favorites.findFavorite(favoriteId, userId).orElse(current);
    }

    // ---- progress -----------------------------------------------------------

    public PlaybackProgressPage listProgress(UUID userId, UUID sourceId, ProgressItemType itemType,
                                             String itemRef, Integer page, Integer size) {
        int pageIndex = page == null ? 0 : Math.max(0, page);
        int pageSize = size == null ? 50 : Math.clamp(size, 1, MAX_PAGE_SIZE);
        String ref = (itemRef == null || itemRef.isBlank()) ? null : itemRef;

        List<PlaybackProgress> items =
                progress.findPage(userId, sourceId, itemType, ref, pageIndex, pageSize);
        long total = progress.count(userId, sourceId, itemType, ref);

        return new PlaybackProgressPage(items, pageIndex, pageSize, total,
                (int) Math.ceil((double) total / pageSize));
    }

    /**
     * Saves a position.
     *
     * <p>The source is checked before it is written, and reported as
     * {@code SOURCE_NOT_FOUND} rather than {@code FORBIDDEN} when it belongs to
     * somebody else — the same rule the rest of this service follows, so these
     * endpoints cannot be used to discover which identifiers exist.
     *
     * <p>The item itself is <b>not</b> checked, and cannot be: {@code item_ref} is
     * minted by the user's own panel and this server holds no table to resolve it
     * against for an episode. What the check is worth is that a position cannot be
     * filed under a source the caller does not own — which is the whole point of
     * the source being in the key.
     */
    public PlaybackProgress saveProgress(UUID userId, SaveProgressRequest request) {
        sources.findOwned(request.getSourceId(), userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.SOURCE_NOT_FOUND,
                        "No such source on this account"));

        return progress.upsert(userId, request.getSourceId(), request.getItemType(),
                request.getItemRef(), request.getPositionMs(), request.getDurationMs());
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
