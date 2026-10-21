package tv.lumo.android.network.generated.api

import tv.lumo.android.network.generated.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import tv.lumo.android.network.generated.model.DeleteAccountRequest
import tv.lumo.android.network.generated.model.DeviceList
import tv.lumo.android.network.generated.model.Entitlement
import tv.lumo.android.network.generated.model.Problem
import tv.lumo.android.network.generated.model.UpdateUserRequest
import tv.lumo.android.network.generated.model.User

interface AccountApi {
    /**
     * POST me/delete
     * Permanently delete the authenticated account
     * Deletes the caller&#39;s account and **everything attached to it**, at once and irreversibly (ADR 0014). There is no grace period, no soft delete and no way back: the same email can register again afterwards, as a new account with nothing in it.  A &#x60;POST&#x60; to an action rather than a &#x60;DELETE /me&#x60; with a body: a request body on &#x60;DELETE&#x60; has no defined meaning in HTTP and is dropped by some intermediaries and client libraries, and the password has to travel.  **Confirmation.** The account password is required, even though the call is authenticated. A phone left unlocked on a table is a valid session; it must not be enough to erase the account. A wrong password answers &#x60;403 INVALID_CREDENTIALS&#x60; — not &#x60;401&#x60;, which clients rightly read as \&quot;the session is over\&quot; and would answer by refreshing or by signing out. An account that never had a password (created through Google) sets one first with &#x60;POST /auth/password/forgot&#x60;.  **What is deleted**, in the same transaction:  - the user (email, password hash, display name, locale); - their sources, including the Xtream credentials encrypted at rest; - everything ingested from those sources: categories, channels, films,   series, seasons, episodes, programme guide; - favourites and favourite groups, the watchlist and its intentions,   playback progress and watched intervals, recent channels; - every linked device, every refresh token, pending TV activations,   email-verification and password-reset tokens, SSO links; - the entitlement row.  Nothing of the account is kept for analytics, and no copy is moved aside. Database backups age out on their own retention schedule.  **Other devices.** Every session of the account ends with it, not only the caller&#39;s. Another device&#39;s next call answers &#x60;401 UNAUTHENTICATED&#x60;, including with an access token that has not expired yet, and its refresh token no longer exists (&#x60;401 REFRESH_TOKEN_INVALID&#x60;): each client then purges its local session as for any ended session.  A synchronisation running in the background for one of the deleted sources stops writing at its next step and leaves nothing behind. 
     * Responses:
     *  - 204: The account and everything attached to it are gone.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 403: The password does not match the account (`INVALID_CREDENTIALS`). Nothing was deleted. 
     *  - 429: Rate limit exceeded (`RATE_LIMITED`).
     *
     * @param deleteAccountRequest 
     * @return [Unit]
     */
    @POST("me/delete")
    suspend fun deleteCurrentUser(@Body deleteAccountRequest: DeleteAccountRequest): Response<Unit>

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
