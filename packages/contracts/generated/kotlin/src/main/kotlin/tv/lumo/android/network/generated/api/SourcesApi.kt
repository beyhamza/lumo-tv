package tv.lumo.android.network.generated.api

import tv.lumo.android.network.generated.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import tv.lumo.android.network.generated.model.CreateSourceRequest
import tv.lumo.android.network.generated.model.Problem
import tv.lumo.android.network.generated.model.Source
import tv.lumo.android.network.generated.model.SourceList
import tv.lumo.android.network.generated.model.UpdateSourceRequest

interface SourcesApi {
    /**
     * POST sources
     * Register a source, then ingest it asynchronously
     * Two distinct error surfaces, deliberately:  1. **Synchronous validation** — reachability and credentials are checked    before answering, within a bounded timeout. A failure here is a &#x60;422&#x60;    carrying an &#x60;IngestionErrorCode&#x60; and **no source is created**. This is    what makes \&quot;your credentials were refused by the server\&quot; appear while    the user is still looking at the form (US-06). 2. **Asynchronous ingestion** — once validation passes the response is    &#x60;202&#x60; with &#x60;status: PENDING&#x60;. Catalogue parsing runs in the    background. A failure there is *not* an HTTP error: the source moves    to &#x60;status: ERROR&#x60; and carries the reason in &#x60;error_code&#x60;, which the    client reads while polling &#x60;GET /sources/{id}&#x60;.  An Xtream &#x60;host&#x60; is normalised server-side: with or without a scheme, with or without a port, with or without a trailing slash. Tolerate and normalise rather than reject.  &#x60;kind: M3U_FILE&#x60; cannot be created through this JSON endpoint — see the note on &#x60;SourceKind&#x60;. 
     * Responses:
     *  - 202: Validated and accepted. Ingestion runs in the background; the source is returned in `PENDING`. Poll `GET /sources/{id}` until `READY` or `ERROR`. 
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 409: The plan's source quota is already used up (`SOURCE_LIMIT_REACHED`). No source was created and nothing was validated: the check happens before the user's own server is contacted.  The client reads the quota from `Entitlement.max_sources` and should not have offered the form — this response is the backstop, not the nominal path. 
     *  - 422: Synchronous validation of the source failed. `code` is one of the `IngestionErrorCode` values and is what the client translates into an actionable message.  This is the number-one friction point of onboarding. \"Something went wrong\" loses users here; \"your credentials were refused by the server\" recovers them. 
     *  - 429: Rate limit exceeded (`RATE_LIMITED`).
     *
     * @param createSourceRequest 
     * @return [Source]
     */
    @POST("sources")
    suspend fun createSource(@Body createSourceRequest: CreateSourceRequest): Response<Source>

    /**
     * DELETE sources/{id}
     * Delete a source and everything ingested from it
     * Cascades: categories, channels, EPG programmes and favourites belonging to this source are removed with it. Irreversible. 
     * Responses:
     *  - 204: Source and its ingested data deleted.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such source on this account (`SOURCE_NOT_FOUND`).
     *
     * @param id Resource identifier.
     * @return [Unit]
     */
    @DELETE("sources/{id}")
    suspend fun deleteSource(@Path("id") id: java.util.UUID): Response<Unit>

    /**
     * GET sources/{id}
     * One source, with its current ingestion status
     * The endpoint clients poll after &#x60;POST /sources&#x60; or &#x60;POST /sources/{id}/sync&#x60;. &#x60;status&#x60; and &#x60;error_code&#x60; carry the outcome of the background ingestion. 
     * Responses:
     *  - 200: The source.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such source on this account (`SOURCE_NOT_FOUND`).
     *
     * @param id Resource identifier.
     * @return [Source]
     */
    @GET("sources/{id}")
    suspend fun getSource(@Path("id") id: java.util.UUID): Response<Source>

    /**
     * GET sources
     * The user&#39;s sources
     * 
     * Responses:
     *  - 200: Every source owned by the caller.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *
     * @return [SourceList]
     */
    @GET("sources")
    suspend fun listSources(): Response<SourceList>

    /**
     * POST sources/{id}/sync
     * Force a re-synchronisation
     * 
     * Responses:
     *  - 202: Accepted. The source is returned in `SYNCING`.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such source on this account (`SOURCE_NOT_FOUND`).
     *  - 409: A synchronisation is already running for this source (`SOURCE_SYNC_IN_PROGRESS`).
     *  - 429: Too many manual synchronisations (`SOURCE_SYNC_RATE_LIMITED`).  This limit protects the *user's own* IPTV server: hammering a third-party panel gets their account throttled or banned. It is a product safeguard, not a capacity one (ADR 0005). 
     *
     * @param id Resource identifier.
     * @return [Source]
     */
    @POST("sources/{id}/sync")
    suspend fun syncSource(@Path("id") id: java.util.UUID): Response<Source>

    /**
     * PATCH sources/{id}
     * Update a source
     * Omitted properties are left unchanged. Changing any property that affects ingestion (&#x60;host&#x60;, &#x60;username&#x60;, &#x60;password&#x60;, &#x60;m3u_url&#x60;, &#x60;epg_url&#x60;) moves the source back to &#x60;PENDING&#x60; and triggers a fresh ingestion.  &#x60;password&#x60; is write-only. It is re-encrypted with AES-256-GCM and is never echoed back. 
     * Responses:
     *  - 200: The updated source.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such source on this account (`SOURCE_NOT_FOUND`).
     *  - 422: Synchronous validation of the source failed. `code` is one of the `IngestionErrorCode` values and is what the client translates into an actionable message.  This is the number-one friction point of onboarding. \"Something went wrong\" loses users here; \"your credentials were refused by the server\" recovers them. 
     *
     * @param id Resource identifier.
     * @param updateSourceRequest 
     * @return [Source]
     */
    @PATCH("sources/{id}")
    suspend fun updateSource(@Path("id") id: java.util.UUID, @Body updateSourceRequest: UpdateSourceRequest): Response<Source>

}
