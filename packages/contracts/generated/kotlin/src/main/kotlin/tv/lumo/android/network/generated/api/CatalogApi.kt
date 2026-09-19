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
import tv.lumo.android.network.generated.model.EpisodePage
import tv.lumo.android.network.generated.model.EpisodePlaybackInfo
import tv.lumo.android.network.generated.model.PlaybackInfo
import tv.lumo.android.network.generated.model.Problem
import tv.lumo.android.network.generated.model.SeriesDetail
import tv.lumo.android.network.generated.model.SeriesPage
import tv.lumo.android.network.generated.model.VodItem
import tv.lumo.android.network.generated.model.VodItemPage
import tv.lumo.android.network.generated.model.VodPlaybackInfo

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
     *  - 409: The source cannot serve playback right now:  - `SOURCE_NOT_READY` — an ingestion is pending or running, or none   has ever succeeded. A source in `ERROR` that still holds a   previous catalogue **does** play: a provider that was down at the   hour of the automatic refresh must not cost the user their   evening; - `SOURCE_AUTH_FAILED` — the last ingestion failed because the   provider refused the credentials. The stream would be refused   too, and the useful message is that one; - `SOURCE_EXPIRED` — the user's Xtream account has expired, as   reported by the panel or by the last ingestion; - `SOURCE_MAX_CONNECTIONS` — the subscription's simultaneous-stream   limit is reached. The client explains that the *user's own*   subscription caps concurrent streams (US-09). 
     *
     * @param id Resource identifier.
     * @return [PlaybackInfo]
     */
    @GET("channels/{id}/playback")
    suspend fun getChannelPlayback(@Path("id") id: java.util.UUID): Response<PlaybackInfo>

    /**
     * GET episodes/{id}/playback
     * Obtain the stream URL for one episode, on demand
     * The third of the family, and everything written on &#x60;GET /channels/{id}/playback&#x60; and &#x60;GET /vod/{id}/playback&#x60; applies here unchanged: issued at the moment of playback after checking ownership, never logged, never cached in a shared store, and opened by the player directly against the user&#39;s own server.  A third operation rather than a shared one for the reason there are already two: three id spaces, and an identifier that could not be resolved without being told which one it belongs to.  **An episode&#39;s URL is built like a film&#39;s**, from the panel&#39;s identifier and container extension: &#x60;/series/{user}/{pass}/{id}.{ext}&#x60;. The path segment differs from a film&#39;s &#x60;/movie/&#x60;, and that is the only difference a client never sees. 
     * Responses:
     *  - 200: Playback details for this episode.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such episode, or it does not belong to a source owned by the caller (`EPISODE_NOT_FOUND`). A `404` and not a `403`, so the endpoint cannot be used to probe for identifiers. 
     *  - 409: The source cannot serve playback right now — `SOURCE_NOT_READY`, `SOURCE_AUTH_FAILED`, `SOURCE_EXPIRED`, `SOURCE_MAX_CONNECTIONS`. The same four as for a channel and a film, meaning the same things: an episode counts against a subscription's simultaneous-stream ceiling exactly as they do. 
     *
     * @param id Resource identifier.
     * @return [EpisodePlaybackInfo]
     */
    @GET("episodes/{id}/playback")
    suspend fun getEpisodePlayback(@Path("id") id: java.util.UUID): Response<EpisodePlaybackInfo>

    /**
     * GET series/{id}
     * One series, with its seasons and episodes
     * The tree, and **the only operation in this API that can be slow on its first call**.  On an Xtream panel the whole tree comes from &#x60;get_series_info&#x60;, which takes one series identifier and answers with every season and every episode. It is one call to the *user&#39;s own server*, made when somebody opens a series, and cached afterwards.  **The cache expires, unlike a film&#39;s synopsis.** A film&#39;s plot never changes; a series in production gains an episode a week. So this cache has a validity period — short and uniform, a few hours — rather than a clever one. Nothing in the data distinguishes a series that ended in 2011 from one airing tonight, and a rule that pretended otherwise would be wrong in the direction nobody notices: a viewer who cannot see the episode that came out this morning.  &lt;h3&gt;What a client must plan for&lt;/h3&gt;  A series opened before answers from the cache, immediately. A series never opened costs a round trip to somebody&#39;s provider, which can take seconds and can fail — which is why the listing already carries the poster, the title and the year. A detail screen draws from what it has and fills the tree in when it arrives.  &lt;h3&gt;The failure that must not be collapsed&lt;/h3&gt;  &#x60;404&#x60; means **this series does not exist**, and it is final. &#x60;503&#x60; means **the panel did not answer**, and it is worth retrying.  A client that showed \&quot;series not found\&quot; on a network fault would send somebody looking for a series their provider still has. These are two different sentences and the codes keep them apart: &#x60;SERIES_NOT_FOUND&#x60; against &#x60;SOURCE_UNREACHABLE&#x60;. 
     * Responses:
     *  - 200: The series and its tree. `plot` is null when the source supplied none. 
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such series on a source owned by the caller (`SERIES_NOT_FOUND`), reported as `404` and not `403` so the endpoint cannot be used to probe for identifiers. 
     *  - 503: The user's panel could not be reached or refused (`SOURCE_UNREACHABLE`, `SOURCE_AUTH_FAILED`, `SOURCE_EXPIRED`), and no cached tree is available to serve instead.  **Distinct from `404` by design.** The series exists; what failed is the call that fills in its seasons. Retrying is the right advice, and a client that said \"not found\" here would give the wrong one. 
     *
     * @param id Resource identifier.
     * @return [SeriesDetail]
     */
    @GET("series/{id}")
    suspend fun getSeries(@Path("id") id: java.util.UUID): Response<SeriesDetail>

    /**
     * GET vod/{id}
     * One film, with its synopsis
     * The only operation that returns a populated &#x60;plot&#x60;, and the reason the listing does not.  **A film&#39;s synopsis costs a call to the user&#39;s own server.** On an Xtream panel it comes from &#x60;get_vod_info&#x60;, which takes one identifier and answers for one film. Fetching it for a catalogue of thirty thousand at every synchronisation is not slow — it is the kind of traffic that gets our address banned by somebody&#39;s provider. So it is fetched when a person opens a film, and cached from then on.  **The consequences a client should plan for.** A film opened before is instant. A film never opened costs one round trip, which is why the listing already carries the poster, the title and the year: a detail screen has everything it needs to draw immediately, and only the synopsis arrives late.  **A panel that refuses is not an error here.** The film is returned with whatever is already known and &#x60;plot&#x60; null. The synopsis is a comfort; the film is the product, and the playback operation does not depend on this one. 
     * Responses:
     *  - 200: The film. `plot` is null when the source could not supply one.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such film on a source owned by the caller (`VOD_ITEM_NOT_FOUND`), reported as `404` and not `403` for the reason given on the playback operation. 
     *
     * @param id Resource identifier.
     * @return [VodItem]
     */
    @GET("vod/{id}")
    suspend fun getVodItem(@Path("id") id: java.util.UUID): Response<VodItem>

    /**
     * GET vod/{id}/playback
     * Obtain the stream URL for one film, on demand
     * The twin of &#x60;GET /channels/{id}/playback&#x60;, and everything written there applies here unchanged: the URL is issued at the moment of playback, after checking that the film belongs to a source owned by the caller; it is never logged, never cached in a shared store, never handed to anyone but its owner; and the player opens it directly against the user&#39;s own server, so the media does not transit through Lumo.  Two operations rather than one because they address two id spaces — a film is not a channel and the identifier could not be resolved without being told which of the two it is.  **A film&#39;s URL is built, a channel&#39;s is stored.** For an Xtream source the panel gives an identifier and a container extension, and this endpoint assembles &#x60;/movie/{user}/{pass}/{id}.{ext}&#x60;; an M3U playlist carries the whole URL already (&#x60;adr/0009&#x60;). The client sees neither difference. 
     * Responses:
     *  - 200: Playback details for this film.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such film, or it does not belong to a source owned by the caller (`VOD_ITEM_NOT_FOUND`). Non-ownership is a `404` and not a `403`, so the endpoint cannot be used to probe for identifiers. 
     *  - 409: The source cannot serve playback right now — `SOURCE_NOT_READY`, `SOURCE_AUTH_FAILED`, `SOURCE_EXPIRED`, `SOURCE_MAX_CONNECTIONS`. The same four as for a channel, and they mean the same things: a subscription's simultaneous-stream limit counts a film exactly as it counts a channel. 
     *
     * @param id Resource identifier.
     * @return [VodPlaybackInfo]
     */
    @GET("vod/{id}/playback")
    suspend fun getVodPlayback(@Path("id") id: java.util.UUID): Response<VodPlaybackInfo>

    /**
     * GET sources/{id}/categories
     * Categories of a source
     * 
     * Responses:
     *  - 200: Categories ordered by `position`, each with its channel count.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such source on this account (`SOURCE_NOT_FOUND`).
     *  - 409: No catalogue has been ingested from this source yet (`SOURCE_NOT_READY`): `Source.last_synced_at` is null. The client keeps polling `GET /sources/{id}`.  This is about the *first* ingestion only. Once one has succeeded the catalogue stays readable whatever `status` says — during a re-synchronisation and after a failed one — because ingestion updates rows in place and never empties them. What is served then is the previous catalogue, and `status`, `last_synced_at` and `last_error_at` are what the client uses to say how old it may be. 
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
     *  - 409: No catalogue has been ingested from this source yet (`SOURCE_NOT_READY`): `Source.last_synced_at` is null. The client keeps polling `GET /sources/{id}`.  This is about the *first* ingestion only. Once one has succeeded the catalogue stays readable whatever `status` says — during a re-synchronisation and after a failed one — because ingestion updates rows in place and never empties them. What is served then is the previous catalogue, and `status`, `last_synced_at` and `last_error_at` are what the client uses to say how old it may be. 
     *
     * @param id Resource identifier.
     * @param categoryId Restrict to one category. (optional)
     * @param q Free-text search on the channel name, typo-tolerant (trigram). (optional)
     * @param ids Resolve these channels, and only these. Repeatable: &#x60;?ids&#x3D;…&amp;ids&#x3D;…&#x60;.  **What it is for.** &#x60;Favorite&#x60; and &#x60;RecentChannel&#x60; carry identifiers and nothing else, deliberately — a name copied onto them would be a name the next ingestion has already changed. A client with a local catalogue resolves those identifiers against it; a client without one has no way to turn a favourite into a row a person can read. This parameter is that way, and it is why the two schemas can stay as they are.  **Semantics.** Composes with &#x60;categoryId&#x60; and &#x60;q&#x60; — every filter present narrows the same result. The order is unchanged (category, then &#x60;position&#x60;): the caller already holds the order it wants, and a rail sorted by *its* rule is the caller&#39;s job, not the query&#39;s. &#x60;total_elements&#x60; counts the matches, so a client can tell how many of the identifiers it sent still exist.  **Unknown identifiers are absent, not an error.** A channel dropped by the last re-synchronisation, or one belonging to another source or another account, is simply not in the answer. A 404 here would turn a stale favourite into a broken screen, and would let a caller probe for channel ids that are not theirs.  Bounded at 100, which is a lookup for a rail and not a bulk export of the catalogue: the paginated form above is how a catalogue is read.  (optional)
     * @param page Zero-based page index. (optional, default to 0)
     * @param size Page size. Capped server-side so a large catalogue cannot be pulled in one call. (optional, default to 50)
     * @return [ChannelPage]
     */
    @GET("sources/{id}/channels")
    suspend fun listChannels(@Path("id") id: java.util.UUID, @Query("categoryId") categoryId: java.util.UUID? = null, @Query("q") q: kotlin.String? = null, @Query("ids") ids: @JvmSuppressWildcards kotlin.collections.List<java.util.UUID>? = null, @Query("page") page: kotlin.Int? = 0, @Query("size") size: kotlin.Int? = 50): Response<ChannelPage>

    /**
     * GET sources/{id}/series
     * Series of a source, paginated
     * The same shape as &#x60;GET /sources/{id}/channels&#x60; and &#x60;GET /sources/{id}/vod&#x60;, down to the parameter names. A client reuses the pagination, the search and the identifier lookup it already wrote.  **Flat, and that is the whole point of separating it from the tree.** This answers from what the synchronisation stored — &#x60;get_series&#x60; on an Xtream panel, one call for the whole catalogue. Seasons and episodes are not here and must not be: fetching them would mean one call to the user&#39;s own server *per series*, and a panel with eight hundred series turns a synchronisation into eight hundred requests against somebody&#39;s provider. That is &#x60;GET /series/{id}&#x60;, on demand.  **No &#x60;plot&#x60; here**, for the reason it is absent from the film listing.  **An M3U source always answers an empty page.** Series are an Xtream feature (&#x60;adr/0010&#x60;): a playlist declares no season and no episode, and this API does not reconstruct a tree from titles. The empty page is the honest answer, and the client explains the absence on the source&#39;s own page rather than as an empty tab. 
     * Responses:
     *  - 200: A page of series, ordered by category then `position`.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such source on this account (`SOURCE_NOT_FOUND`).
     *  - 409: No catalogue has been ingested from this source yet (`SOURCE_NOT_READY`): `Source.last_synced_at` is null. The client keeps polling `GET /sources/{id}`.  This is about the *first* ingestion only. Once one has succeeded the catalogue stays readable whatever `status` says — during a re-synchronisation and after a failed one — because ingestion updates rows in place and never empties them. What is served then is the previous catalogue, and `status`, `last_synced_at` and `last_error_at` are what the client uses to say how old it may be. 
     *
     * @param id Resource identifier.
     * @param categoryId Restrict to one category. Its &#x60;content_type&#x60; is &#x60;SERIES&#x60;. (optional)
     * @param q Free-text search on the title. Case-insensitive substring, as on the other two listings.  (optional)
     * @param ids Resolve these series, and only these. Repeatable, bounded at 100, same semantics as &#x60;ids&#x60; everywhere else — unknown identifiers are absent from the answer rather than an error.  **Send &#x60;size&#x60; with it.** The default page is 50, so a hundred identifiers asked for without it come back half answered, with a &#x60;200&#x60; and nothing to say the rest was dropped.  (optional)
     * @param page Zero-based page index. (optional, default to 0)
     * @param size Page size. Capped server-side so a large catalogue cannot be pulled in one call. (optional, default to 50)
     * @return [SeriesPage]
     */
    @GET("sources/{id}/series")
    suspend fun listSeries(@Path("id") id: java.util.UUID, @Query("categoryId") categoryId: java.util.UUID? = null, @Query("q") q: kotlin.String? = null, @Query("ids") ids: @JvmSuppressWildcards kotlin.collections.List<java.util.UUID>? = null, @Query("page") page: kotlin.Int? = 0, @Query("size") size: kotlin.Int? = 50): Response<SeriesPage>

    /**
     * GET sources/{id}/vod
     * Films of a source, paginated
     * The same shape as &#x60;GET /sources/{id}/channels&#x60;, down to the parameter names, and that is the point: a client reuses the pagination, the search and the identifier lookup it already wrote instead of growing a second set that drifts from the first.  **No &#x60;stream_url&#x60; here either**, for the reason given on the channel listing: a page of films does not carry a page of credential-bearing URLs. Playback URLs come from &#x60;GET /vod/{id}/playback&#x60;, one at a time.  **No &#x60;plot&#x60; here.** It is loaded when somebody opens a film, not when they scroll past it — see &#x60;VodItem.plot&#x60;. 
     * Responses:
     *  - 200: A page of films, ordered by category then `position`.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such source on this account (`SOURCE_NOT_FOUND`).
     *  - 409: No catalogue has been ingested from this source yet (`SOURCE_NOT_READY`): `Source.last_synced_at` is null. The client keeps polling `GET /sources/{id}`.  This is about the *first* ingestion only. Once one has succeeded the catalogue stays readable whatever `status` says — during a re-synchronisation and after a failed one — because ingestion updates rows in place and never empties them. What is served then is the previous catalogue, and `status`, `last_synced_at` and `last_error_at` are what the client uses to say how old it may be. 
     *
     * @param id Resource identifier.
     * @param categoryId Restrict to one category. Its &#x60;content_type&#x60; is &#x60;VOD&#x60;. (optional)
     * @param q Free-text search on the title. Case-insensitive substring, as on the channel listing — the trigram index makes it fast, not approximate.  (optional)
     * @param ids Resolve these films, and only these. Repeatable, bounded at 100, and it composes with the other filters — the same semantics as &#x60;ids&#x60; on the channel listing, including that unknown identifiers are absent from the answer rather than an error.  **Send &#x60;size&#x60; with it.** The default page is 50, so a hundred identifiers asked for without it come back half answered, with a &#x60;200&#x60; and nothing to say the rest was dropped.  (optional)
     * @param page Zero-based page index. (optional, default to 0)
     * @param size Page size. Capped server-side so a large catalogue cannot be pulled in one call. (optional, default to 50)
     * @return [VodItemPage]
     */
    @GET("sources/{id}/vod")
    suspend fun listVod(@Path("id") id: java.util.UUID, @Query("categoryId") categoryId: java.util.UUID? = null, @Query("q") q: kotlin.String? = null, @Query("ids") ids: @JvmSuppressWildcards kotlin.collections.List<java.util.UUID>? = null, @Query("page") page: kotlin.Int? = 0, @Query("size") size: kotlin.Int? = 50): Response<VodItemPage>

    /**
     * GET sources/{id}/episodes
     * Resolve episodes by identifier
     * **A resolver, not a listing, and &#x60;ids&#x60; is required.**  It exists for one caller: a \&quot;continue watching\&quot; rail. &#x60;GET /me/progress&#x60; returns identifiers and positions — not titles, not posters, and not the series an episode belongs to. Something has to turn those rows back into something a screen can draw, and this is it, in one request rather than one per row.  That lesson was learnt the expensive way. Sprint 5 shipped the film resume rail and only then discovered that a saved position could not be resolved back to a film; the fix was to settle what &#x60;item_ref&#x60; holds (&#x60;SaveProgressRequest.item_ref&#x60;). This operation is the same problem, seen before it cost a sprint.  **&#x60;ids&#x60; is required on purpose.** Without it this would be a listing over every episode of every series of a source — tens of thousands of rows nobody has a use for, and a page of them is not a screen anybody would build. A resolver that can only resolve cannot be misused as a crawler.  Each &#x60;Episode&#x60; carries its &#x60;series_id&#x60;, which is what lets a rail group rows by series and then resolve those with &#x60;GET /sources/{id}/series?ids&#x3D;&#x60;. 
     * Responses:
     *  - 200: The episodes that exist and belong to a source owned by the caller. Order is not guaranteed to match `ids`: the caller holds the order it wants, and re-sorting here would be guessing which one. 
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such source on this account (`SOURCE_NOT_FOUND`).
     *  - 409: No catalogue has been ingested from this source yet (`SOURCE_NOT_READY`): `Source.last_synced_at` is null. The client keeps polling `GET /sources/{id}`.  This is about the *first* ingestion only. Once one has succeeded the catalogue stays readable whatever `status` says — during a re-synchronisation and after a failed one — because ingestion updates rows in place and never empties them. What is served then is the previous catalogue, and `status`, `last_synced_at` and `last_error_at` are what the client uses to say how old it may be. 
     *
     * @param id Resource identifier.
     * @param ids The episodes to resolve. Repeatable, bounded at 100, unknown identifiers absent from the answer rather than an error.  **Send &#x60;size&#x60; with it**, for the reason repeated on every &#x60;ids&#x60; parameter in this document. 
     * @param page Zero-based page index. (optional, default to 0)
     * @param size Page size. Capped server-side so a large catalogue cannot be pulled in one call. (optional, default to 50)
     * @return [EpisodePage]
     */
    @GET("sources/{id}/episodes")
    suspend fun resolveEpisodes(@Path("id") id: java.util.UUID, @Query("ids") ids: @JvmSuppressWildcards kotlin.collections.List<java.util.UUID>, @Query("page") page: kotlin.Int? = 0, @Query("size") size: kotlin.Int? = 50): Response<EpisodePage>

}
