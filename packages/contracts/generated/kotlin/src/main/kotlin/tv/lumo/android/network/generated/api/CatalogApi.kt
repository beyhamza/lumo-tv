package tv.lumo.android.network.generated.api

import tv.lumo.android.network.generated.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import tv.lumo.android.network.generated.model.CategoryList
import tv.lumo.android.network.generated.model.ChannelPage
import tv.lumo.android.network.generated.model.ContentType
import tv.lumo.android.network.generated.model.EpgProgrammeList
import tv.lumo.android.network.generated.model.PlaybackInfo
import tv.lumo.android.network.generated.model.Problem

interface CatalogApi {
    /**
     * GET channels/{id}/epg
     * Programme guide for one channel over a time range
     * Programmes are matched on the channel&#39;s &#x60;tvg_id&#x60; within its source. Retention is a sliding window from D-1 to D+3; a range outside it returns an empty list rather than an error.  A channel with no &#x60;tvg_id&#x60;, or a source with no EPG URL, returns an empty list. 
     * Responses:
     *  - 200: Programmes overlapping the range, ordered by `starts_at`.
     *  - 400: The range is invalid — `to` before `from`, or wider than four days (`VALIDATION_FAILED`). 
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such channel on a source owned by the caller (`CHANNEL_NOT_FOUND`).
     *
     * @param id Resource identifier.
     * @param from Inclusive lower bound. Defaults to now. (optional)
     * @param to Exclusive upper bound. Defaults to &#x60;from&#x60; + 24 h. At most 4 days after &#x60;from&#x60;. (optional)
     * @return [EpgProgrammeList]
     */
    @GET("channels/{id}/epg")
    suspend fun getChannelEpg(@Path("id") id: java.util.UUID, @Query("from") from: java.time.OffsetDateTime? = null, @Query("to") to: java.time.OffsetDateTime? = null): Response<EpgProgrammeList>

    /**
     * GET channels/{id}/playback
     * Obtain the stream URL for one channel, on demand
     * **The only operation in this API that emits a &#x60;stream_url&#x60;.**  Deliberately separated from the listing: the URL is issued at the moment of playback, after verifying that the channel belongs to a source owned by the caller. It is never logged, never cached in a shared store, and never handed to anyone but its owner.  The player opens this URL directly against the user&#39;s IPTV server. The media does not transit through Lumo. 
     * Responses:
     *  - 200: Playback details for this channel.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such channel, or it does not belong to a source owned by the caller (`CHANNEL_NOT_FOUND`). Non-ownership is reported as `404`, not `403`, so the endpoint cannot be used to probe for channel ids. 
     *  - 409: The source cannot serve playback right now:  - `SOURCE_NOT_READY` — ingestion has not completed; - `SOURCE_EXPIRED` — the user's Xtream account has expired; - `SOURCE_MAX_CONNECTIONS` — the subscription's simultaneous-stream   limit is reached. The client explains that the *user's own*   subscription caps concurrent streams (US-09). 
     *
     * @param id Resource identifier.
     * @return [PlaybackInfo]
     */
    @GET("channels/{id}/playback")
    suspend fun getChannelPlayback(@Path("id") id: java.util.UUID): Response<PlaybackInfo>

    /**
     * GET sources/{id}/categories
     * Categories of a source
     * 
     * Responses:
     *  - 200: Categories ordered by `position`, each with its channel count.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such source on this account (`SOURCE_NOT_FOUND`).
     *  - 409: The source has not finished ingesting (`SOURCE_NOT_READY`). The client keeps polling `GET /sources/{id}`. 
     *
     * @param id Resource identifier.
     * @param contentType Restrict to one content type. Omitted, every category is returned. (optional)
     * @return [CategoryList]
     */
    @GET("sources/{id}/categories")
    suspend fun listCategories(@Path("id") id: java.util.UUID, @Query("contentType") contentType: ContentType? = null): Response<CategoryList>

    /**
     * GET sources/{id}/channels
     * Channels of a source, paginated
     * **No &#x60;stream_url&#x60; is present in this response.** A list of a thousand channels does not carry a thousand stream URLs. Playback URLs are obtained one at a time from &#x60;GET /channels/{id}/playback&#x60;. 
     * Responses:
     *  - 200: A page of channels, ordered by category then `position`.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such source on this account (`SOURCE_NOT_FOUND`).
     *  - 409: The source has not finished ingesting (`SOURCE_NOT_READY`). The client keeps polling `GET /sources/{id}`. 
     *
     * @param id Resource identifier.
     * @param categoryId Restrict to one category. (optional)
     * @param q Free-text search on the channel name, typo-tolerant (trigram). (optional)
     * @param page Zero-based page index. (optional, default to 0)
     * @param size Page size. Capped server-side so a large catalogue cannot be pulled in one call. (optional, default to 50)
     * @return [ChannelPage]
     */
    @GET("sources/{id}/channels")
    suspend fun listChannels(@Path("id") id: java.util.UUID, @Query("categoryId") categoryId: java.util.UUID? = null, @Query("q") q: kotlin.String? = null, @Query("page") page: kotlin.Int? = 0, @Query("size") size: kotlin.Int? = 50): Response<ChannelPage>

}
