package tv.lumo.android.network.generated.api

import tv.lumo.android.network.generated.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import tv.lumo.android.network.generated.model.AppSettings
import tv.lumo.android.network.generated.model.Problem
import tv.lumo.android.network.generated.model.PublicConfig
import tv.lumo.android.network.generated.model.UpdateAppSettingsRequest

interface SettingsApi {
    /**
     * GET admin/settings
     * The application&#39;s configuration
     * Administrators only.
     * Responses:
     *  - 200: The configuration.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 403: The caller is signed in and is not an administrator (`FORBIDDEN`).
     *
     * @return [AppSettings]
     */
    @GET("admin/settings")
    suspend fun getAppSettings(): Response<AppSettings>

    /**
     * GET config
     * The part of the configuration any visitor may read
     * 
     * Responses:
     *  - 200: The public configuration.
     *
     * @return [PublicConfig]
     */
    @GET("config")
    suspend fun getPublicConfig(): Response<PublicConfig>

    /**
     * PUT admin/settings
     * Replace the application&#39;s configuration
     * Administrators only. Plan quotas are not here and will not be: what a plan allows stays out of reach of this screen (ADR 0003, ADR 0015). 
     * Responses:
     *  - 200: The updated configuration.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 403: The caller is signed in and is not an administrator (`FORBIDDEN`).
     *
     * @param updateAppSettingsRequest 
     * @return [AppSettings]
     */
    @PUT("admin/settings")
    suspend fun updateAppSettings(@Body updateAppSettingsRequest: UpdateAppSettingsRequest): Response<AppSettings>

}
