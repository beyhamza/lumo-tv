package tv.lumo.api.userdata;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tv.lumo.api.auth.CurrentUser;
import tv.lumo.api.generated.api.UserdataApi;
import tv.lumo.api.generated.model.AddFavoriteRequest;
import tv.lumo.api.generated.model.CreateFavoriteGroupRequest;
import tv.lumo.api.generated.model.Favorite;
import tv.lumo.api.generated.model.FavoriteGroup;
import tv.lumo.api.generated.model.FavoriteGroupList;
import tv.lumo.api.generated.model.FavoriteList;
import tv.lumo.api.generated.model.PlaybackProgress;
import tv.lumo.api.generated.model.PlaybackProgressPage;
import tv.lumo.api.generated.model.ProgressItemType;
import tv.lumo.api.generated.model.RecentChannel;
import tv.lumo.api.generated.model.RecentChannelList;
import tv.lumo.api.generated.model.RecordRecentChannelRequest;
import tv.lumo.api.generated.model.SaveProgressRequest;
import tv.lumo.api.generated.model.UpdateFavoriteGroupRequest;
import tv.lumo.api.generated.model.UpdateFavoriteRequest;

/**
 * Implements the generated {@code UserdataApi}.
 *
 * <p>Nothing here takes a user id: the subject of every operation is
 * {@link CurrentUser}, and the id is threaded into the SQL rather than read off a
 * path or a body.
 */
@RestController
public class UserdataController implements UserdataApi {

    private final UserdataService userdata;

    public UserdataController(UserdataService userdata) {
        this.userdata = userdata;
    }

    @Override
    public ResponseEntity<FavoriteList> listFavorites(UUID groupId) {
        return ResponseEntity.ok(new FavoriteList(
                userdata.listFavorites(CurrentUser.requireUserId(), groupId)));
    }

    @Override
    public ResponseEntity<Favorite> addFavorite(AddFavoriteRequest request) {
        Favorite created = userdata.addFavorite(CurrentUser.requireUserId(), request);
        return ResponseEntity.created(URI.create("/v1/me/favorites/" + created.getId()))
                .body(created);
    }

    @Override
    public ResponseEntity<Void> removeFavorite(UUID id) {
        userdata.removeFavorite(CurrentUser.requireUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<FavoriteGroupList> listFavoriteGroups() {
        return ResponseEntity.ok(new FavoriteGroupList(
                userdata.listGroups(CurrentUser.requireUserId())));
    }

    @Override
    public ResponseEntity<FavoriteGroup> createFavoriteGroup(CreateFavoriteGroupRequest request) {
        FavoriteGroup created = userdata.createGroup(CurrentUser.requireUserId(), request);
        return ResponseEntity.created(URI.create("/v1/me/favorite-groups/" + created.getId()))
                .body(created);
    }

    @Override
    public ResponseEntity<FavoriteGroup> updateFavoriteGroup(UUID id, UpdateFavoriteGroupRequest request) {
        return ResponseEntity.ok(userdata.updateGroup(CurrentUser.requireUserId(), id, request));
    }

    @Override
    public ResponseEntity<Void> deleteFavoriteGroup(UUID id) {
        userdata.deleteGroup(CurrentUser.requireUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Favorite> updateFavorite(UUID id, UpdateFavoriteRequest request) {
        return ResponseEntity.ok(userdata.updateFavorite(CurrentUser.requireUserId(), id, request));
    }

    @Override
    public ResponseEntity<PlaybackProgressPage> listProgress(ProgressItemType itemType, String itemRef,
                                                             Integer page, Integer size) {
        return ResponseEntity.ok(userdata.listProgress(
                CurrentUser.requireUserId(), itemType, itemRef, page, size));
    }

    @Override
    public ResponseEntity<PlaybackProgress> saveProgress(SaveProgressRequest request) {
        return ResponseEntity.ok(userdata.saveProgress(CurrentUser.requireUserId(), request));
    }

    @Override
    public ResponseEntity<RecentChannelList> listRecentChannels(Integer limit) {
        return ResponseEntity.ok(new RecentChannelList(
                userdata.listRecentChannels(CurrentUser.requireUserId(), limit)));
    }

    @Override
    public ResponseEntity<RecentChannel> recordRecentChannel(RecordRecentChannelRequest request) {
        return ResponseEntity.ok(userdata.recordRecentChannel(
                CurrentUser.requireUserId(), request.getChannelId()));
    }
}
