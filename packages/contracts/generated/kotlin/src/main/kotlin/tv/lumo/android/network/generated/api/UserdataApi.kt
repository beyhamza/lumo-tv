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
import tv.lumo.android.network.generated.model.SaveProgressRequest

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
     * Ordered by &#x60;updated_at&#x60;, most recent first — which is also the order a \&quot;Continue watching\&quot; rail wants.  Without this operation &#x60;PUT /me/progress&#x60; writes into a void: progress could be saved on the phone and never read back on the television, and \&quot;resume across screens\&quot; would be a promise no client could keep.  Passing both &#x60;itemType&#x60; and &#x60;itemRef&#x60; narrows the page to the single matching row, which is how a player looks up one item before opening it. There is deliberately no &#x60;/me/progress/{itemType}/{itemRef}&#x60; variant: &#x60;item_ref&#x60; is an opaque identifier minted by the user&#39;s own panel, and nothing stops it containing a slash or a percent sign. Filtering keeps it in a query parameter, where encoding is unambiguous, instead of a path segment, where it is not. 
     * Responses:
     *  - 200: One page of saved positions, most recently updated first.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *
     * @param itemType Restrict to one kind of item. (optional)
     * @param itemRef Restrict to one item. Combined with &#x60;itemType&#x60; this yields at most one element.  (optional)
     * @param page Zero-based page index. (optional, default to 0)
     * @param size Page size. Capped server-side so a large catalogue cannot be pulled in one call. (optional, default to 50)
     * @return [PlaybackProgressPage]
     */
    @GET("me/progress")
    suspend fun listProgress(@Query("itemType") itemType: ProgressItemType? = null, @Query("itemRef") itemRef: kotlin.String? = null, @Query("page") page: kotlin.Int? = 0, @Query("size") size: kotlin.Int? = 50): Response<PlaybackProgressPage>

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
     * Idempotent upsert keyed on &#x60;(item_type, item_ref)&#x60; for the caller.  Live channels have no progress. Sending &#x60;item_type&#x60; for a live channel is a client bug, not a supported case. 
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

}
