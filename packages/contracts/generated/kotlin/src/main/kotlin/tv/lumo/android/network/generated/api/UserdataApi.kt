package tv.lumo.android.network.generated.api

import tv.lumo.android.network.generated.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import tv.lumo.android.network.generated.model.AddFavoriteRequest
import tv.lumo.android.network.generated.model.CreateFavoriteGroupRequest
import tv.lumo.android.network.generated.model.Favorite
import tv.lumo.android.network.generated.model.FavoriteGroup
import tv.lumo.android.network.generated.model.FavoriteGroupList
import tv.lumo.android.network.generated.model.FavoriteList
import tv.lumo.android.network.generated.model.PlaybackProgress
import tv.lumo.android.network.generated.model.PlaybackProgressPage
import tv.lumo.android.network.generated.model.Problem
import tv.lumo.android.network.generated.model.ProgressItemType
import tv.lumo.android.network.generated.model.RecentChannel
import tv.lumo.android.network.generated.model.RecentChannelList
import tv.lumo.android.network.generated.model.RecordRecentChannelRequest
import tv.lumo.android.network.generated.model.SaveProgressRequest
import tv.lumo.android.network.generated.model.UpdateFavoriteGroupRequest
import tv.lumo.android.network.generated.model.UpdateFavoriteRequest

interface UserdataApi {
    /**
     * POST me/favorites
     * Add a channel to a favourite group
     * &#x60;group_id&#x60; may be omitted: the channel then lands in the account&#39;s default group, which is created on the first add. 
     * Responses:
     *  - 201: The created favourite.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: The channel or the group does not belong to the caller (`CHANNEL_NOT_FOUND`, `FAVORITE_GROUP_NOT_FOUND`). 
     *  - 409: This channel is already in that group (`FAVORITE_ALREADY_EXISTS`).
     *
     * @param addFavoriteRequest 
     * @return [Favorite]
     */
    @POST("me/favorites")
    suspend fun addFavorite(@Body addFavoriteRequest: AddFavoriteRequest): Response<Favorite>

    /**
     * POST me/favorite-groups
     * Create a favourite group
     * 
     * Responses:
     *  - 201: The created group.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 409: A group with this name already exists (`FAVORITE_GROUP_ALREADY_EXISTS`).
     *
     * @param createFavoriteGroupRequest 
     * @return [FavoriteGroup]
     */
    @POST("me/favorite-groups")
    suspend fun createFavoriteGroup(@Body createFavoriteGroupRequest: CreateFavoriteGroupRequest): Response<FavoriteGroup>

    /**
     * DELETE me/favorite-groups/{id}
     * Delete a favourite group, keeping its favourites
     * **The favourites are not deleted.** They move to the account&#39;s default group, appended in their current order, in the same transaction that removes the group.  Deleting a shelf and throwing away the books are two different actions, and nothing on screen distinguishes them — so the destructive reading is not the one this operation takes. A client still tells the user what is about to happen, with the count.  The default group itself cannot be deleted: it is where the others empty into, and removing it would move favourites to a group that has just ceased to exist. 
     * Responses:
     *  - 204: Group deleted; its favourites are now in the default group.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such group on this account (`FAVORITE_GROUP_NOT_FOUND`).
     *  - 409: This is the default group and it cannot be deleted (`FAVORITE_GROUP_NOT_DELETABLE`). 
     *
     * @param id Resource identifier.
     * @return [Unit]
     */
    @DELETE("me/favorite-groups/{id}")
    suspend fun deleteFavoriteGroup(@Path("id") id: java.util.UUID): Response<Unit>

    /**
     * GET me/favorite-groups
     * The user&#39;s favourite groups
     * 
     * Responses:
     *  - 200: Groups ordered by `position`.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *
     * @return [FavoriteGroupList]
     */
    @GET("me/favorite-groups")
    suspend fun listFavoriteGroups(): Response<FavoriteGroupList>

    /**
     * GET me/favorites
     * The user&#39;s favourites
     * 
     * Responses:
     *  - 200: Favourites ordered by group then `position`.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *
     * @param groupId Restrict to one favourite group. (optional)
     * @return [FavoriteList]
     */
    @GET("me/favorites")
    suspend fun listFavorites(@Query("groupId") groupId: java.util.UUID? = null): Response<FavoriteList>

    /**
     * GET me/progress
     * Playback positions saved by this account
     * Ordered by &#x60;updated_at&#x60;, most recent first — which is also the order a \&quot;Continue watching\&quot; rail wants.  Without this operation &#x60;PUT /me/progress&#x60; writes into a void: progress could be saved on the phone and never read back on the television, and \&quot;resume across screens\&quot; would be a promise no client could keep.  Passing &#x60;sourceId&#x60;, &#x60;itemType&#x60; and &#x60;itemRef&#x60; together narrows the page to the single matching row, which is how a player looks up one item before opening it. All three, because the key is all three: &#x60;item_ref&#x60; is minted by the user&#39;s own panel and two subscriptions can use the same value for two different films.  There is deliberately no &#x60;/me/progress/{itemType}/{itemRef}&#x60; variant: &#x60;item_ref&#x60; is opaque and nothing stops it containing a slash or a percent sign. Filtering keeps it in a query parameter, where encoding is unambiguous, instead of a path segment, where it is not. 
     * Responses:
     *  - 200: One page of saved positions, most recently updated first.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *
     * @param sourceId Restrict to one source. Part of an item&#39;s key, not a convenience. (optional)
     * @param itemType Restrict to one kind of item. (optional)
     * @param itemRef Restrict to one item. Combined with &#x60;itemType&#x60; this yields at most one element.  (optional)
     * @param page Zero-based page index. (optional, default to 0)
     * @param size Page size. Capped server-side so a large catalogue cannot be pulled in one call. (optional, default to 50)
     * @return [PlaybackProgressPage]
     */
    @GET("me/progress")
    suspend fun listProgress(@Query("sourceId") sourceId: java.util.UUID? = null, @Query("itemType") itemType: ProgressItemType? = null, @Query("itemRef") itemRef: kotlin.String? = null, @Query("page") page: kotlin.Int? = 0, @Query("size") size: kotlin.Int? = 50): Response<PlaybackProgressPage>

    /**
     * GET me/recent-channels
     * Live channels the user watched recently
     * The television&#39;s first rail, and the reason it is worth anything: it knows what was watched on the phone.  Deliberately **not** part of &#x60;playback_progress&#x60;. A playback position means nothing on a continuous stream, and folding live channels into that table would make &#x60;position_ms&#x60; a required property with no possible value. Two concepts sharing storage because they render in the same rail is the shortcut that bills six months later.  Ordered most recent first, and bounded: this feeds a rail, not a history. The server keeps a rolling window per account and prunes silently. 
     * Responses:
     *  - 200: The most recently watched channels, newest first.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *
     * @param limit Maximum entries to return. (optional, default to 20)
     * @return [RecentChannelList]
     */
    @GET("me/recent-channels")
    suspend fun listRecentChannels(@Query("limit") limit: kotlin.Int? = 20): Response<RecentChannelList>

    /**
     * PUT me/recent-channels
     * Record that a channel was just watched
     * Idempotent upsert keyed on the channel for the caller: watching the same channel again moves it to the top rather than adding a row.  Sent when playback actually starts, not when a channel is focused — a rail built from what the D-pad passed over on its way somewhere is noise, and it is the user&#39;s own history being made worse. 
     * Responses:
     *  - 200: The recorded entry.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such channel on this account (`CHANNEL_NOT_FOUND`).
     *
     * @param recordRecentChannelRequest 
     * @return [RecentChannel]
     */
    @PUT("me/recent-channels")
    suspend fun recordRecentChannel(@Body recordRecentChannelRequest: RecordRecentChannelRequest): Response<RecentChannel>

    /**
     * DELETE me/favorites/{id}
     * Remove a favourite
     * 
     * Responses:
     *  - 204: Favourite removed.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such favourite on this account (`FAVORITE_NOT_FOUND`).
     *
     * @param id Resource identifier.
     * @return [Unit]
     */
    @DELETE("me/favorites/{id}")
    suspend fun removeFavorite(@Path("id") id: java.util.UUID): Response<Unit>

    /**
     * PUT me/progress
     * Save playback progress for a VOD item or an episode
     * Idempotent upsert keyed on &#x60;(source_id, item_type, item_ref)&#x60; for the caller. The source is part of the key rather than a passenger — see &#x60;SaveProgressRequest.source_id&#x60;.  Live channels have no progress. &#x60;ProgressItemType&#x60; has no &#x60;LIVE&#x60; value, and sending one for a channel is a client bug rather than a supported case: a position means nothing on a continuous stream. What a channel gets instead is &#x60;PUT /me/recent-channels&#x60;. 
     * Responses:
     *  - 200: The stored progress.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *
     * @param saveProgressRequest 
     * @return [PlaybackProgress]
     */
    @PUT("me/progress")
    suspend fun saveProgress(@Body saveProgressRequest: SaveProgressRequest): Response<PlaybackProgress>

    /**
     * PATCH me/favorites/{id}
     * Move a favourite, or change its place in its group
     * Omitted properties are left unchanged.  **Why this exists rather than a remove-then-add.** That pair loses the favourite&#39;s &#x60;position&#x60;, and a connection dropped between the two calls loses the favourite itself. Moving is one operation because it is one intention.  &#x60;position&#x60; is the index the favourite takes **within its target group**, counted from zero. The favourites it displaces shift down; a value past the end of the group appends. Positions in a group are always contiguous — a client never has to reason about gaps. 
     * Responses:
     *  - 200: The moved favourite.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such favourite on this account, or the target group does not belong to the caller (`FAVORITE_NOT_FOUND`, `FAVORITE_GROUP_NOT_FOUND`).  A group belonging to somebody else answers `404` and not `403`: a `403` would confirm that the group exists. 
     *  - 409: The channel is already in the target group (`FAVORITE_ALREADY_EXISTS`). Same rule as `POST /me/favorites`: one channel appears at most once per group. 
     *
     * @param id Resource identifier.
     * @param updateFavoriteRequest 
     * @return [Favorite]
     */
    @PATCH("me/favorites/{id}")
    suspend fun updateFavorite(@Path("id") id: java.util.UUID, @Body updateFavoriteRequest: UpdateFavoriteRequest): Response<Favorite>

    /**
     * PATCH me/favorite-groups/{id}
     * Rename a favourite group, or move it in the list
     * Omitted properties are left unchanged.  The default group can be renamed like any other. Renaming it does not clear &#x60;is_default&#x60;: it stays the group &#x60;POST /me/favorites&#x60; falls back to, and the group &#x60;DELETE&#x60; empties others into. 
     * Responses:
     *  - 200: The updated group.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such group on this account (`FAVORITE_GROUP_NOT_FOUND`).
     *  - 409: Another group already carries this name (`FAVORITE_GROUP_ALREADY_EXISTS`).
     *
     * @param id Resource identifier.
     * @param updateFavoriteGroupRequest 
     * @return [FavoriteGroup]
     */
    @PATCH("me/favorite-groups/{id}")
    suspend fun updateFavoriteGroup(@Path("id") id: java.util.UUID, @Body updateFavoriteGroupRequest: UpdateFavoriteGroupRequest): Response<FavoriteGroup>

}
