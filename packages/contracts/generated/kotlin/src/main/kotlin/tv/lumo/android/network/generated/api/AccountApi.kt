package tv.lumo.android.network.generated.api

import tv.lumo.android.network.generated.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import tv.lumo.android.network.generated.model.DeviceList
import tv.lumo.android.network.generated.model.Entitlement
import tv.lumo.android.network.generated.model.Problem
import tv.lumo.android.network.generated.model.UpdateUserRequest
import tv.lumo.android.network.generated.model.User

interface AccountApi {
    /**
     * GET me
     * The authenticated user
     * 
     * Responses:
     *  - 200: The user.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *
     * @return [User]
     */
    @GET("me")
    suspend fun getCurrentUser(): Response<User>

    /**
     * GET me/entitlement
     * The user&#39;s access rights
     * **The only source of truth for access rights** (ADR 0003).  A client never asks a store whether the user is premium. Both Android listings and the web read this single endpoint. In v1 the table is fed by Stripe webhooks; in v2 Play RTDN will write into the same table, with no client-side change.  A user with no subscription has a &#x60;FREE&#x60; / &#x60;ACTIVE&#x60; entitlement; this endpoint never returns &#x60;404&#x60;. 
     * Responses:
     *  - 200: The user's single active entitlement.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *
     * @return [Entitlement]
     */
    @GET("me/entitlement")
    suspend fun getEntitlement(): Response<Entitlement>

    /**
     * GET me/devices
     * Devices linked to the account
     * 
     * Responses:
     *  - 200: The account's devices, most recently seen first.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *
     * @return [DeviceList]
     */
    @GET("me/devices")
    suspend fun listDevices(): Response<DeviceList>

    /**
     * DELETE me/devices/{id}
     * Unlink a device and revoke its tokens
     * Revokes the device&#39;s entire refresh-token chain. That device is signed out at its next call. Removing the calling device is allowed and is equivalent to signing out. 
     * Responses:
     *  - 204: Device unlinked and its tokens revoked.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No such device on this account (`DEVICE_NOT_FOUND`).
     *
     * @param id Resource identifier.
     * @return [Unit]
     */
    @DELETE("me/devices/{id}")
    suspend fun revokeDevice(@Path("id") id: java.util.UUID): Response<Unit>

    /**
     * PATCH me
     * Update display name and locale
     * Only &#x60;display_name&#x60; and &#x60;locale&#x60; are mutable here. Email changes require re-verification and are not part of v1. Omitted properties are left unchanged; an explicit &#x60;null&#x60; clears &#x60;display_name&#x60;. 
     * Responses:
     *  - 200: The updated user.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *
     * @param updateUserRequest 
     * @return [User]
     */
    @PATCH("me")
    suspend fun updateCurrentUser(@Body updateUserRequest: UpdateUserRequest): Response<User>

}
