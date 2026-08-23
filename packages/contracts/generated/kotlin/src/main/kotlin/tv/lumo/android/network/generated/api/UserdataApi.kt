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
import tv.lumo.android.network.generated.model.Problem
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
