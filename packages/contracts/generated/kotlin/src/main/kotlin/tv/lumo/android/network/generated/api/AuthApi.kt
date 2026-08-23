package tv.lumo.android.network.generated.api

import tv.lumo.android.network.generated.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import tv.lumo.android.network.generated.model.ApproveDeviceRequest
import tv.lumo.android.network.generated.model.AuthSession
import tv.lumo.android.network.generated.model.DeviceCodeRequest
import tv.lumo.android.network.generated.model.DeviceCodeResponse
import tv.lumo.android.network.generated.model.DeviceTokenRequest
import tv.lumo.android.network.generated.model.ForgotPasswordRequest
import tv.lumo.android.network.generated.model.GoogleSignInRequest
import tv.lumo.android.network.generated.model.LoginRequest
import tv.lumo.android.network.generated.model.Problem
import tv.lumo.android.network.generated.model.RefreshRequest
import tv.lumo.android.network.generated.model.RegisterRequest
import tv.lumo.android.network.generated.model.ResetPasswordRequest
import tv.lumo.android.network.generated.model.TokenPair

interface AuthApi {
    /**
     * POST auth/device/approve
     * TV activation: approve a user code from an authenticated session
     * Called from &#x60;lumo.tv/activate&#x60; by an already signed-in user. Binds the pending authorization to the caller&#39;s account.  Strictly rate-limited: &#x60;user_code&#x60; is short and guessable by design, so this endpoint is the one an attacker would brute-force. After five attempts the caller is throttled (US-05). 
     * Responses:
     *  - 204: Approved. The television's next poll receives a session.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: No pending authorization carries this code (`DEVICE_CODE_NOT_FOUND`).
     *  - 409: The code was already approved, denied or consumed (`DEVICE_CODE_ALREADY_USED`).
     *  - 410: The code has expired (`DEVICE_CODE_EXPIRED`). The TV displays a new one on its own.
     *  - 429: Rate limit exceeded (`RATE_LIMITED`).
     *
     * @param approveDeviceRequest 
     * @return [Unit]
     */
    @POST("auth/device/approve")
    suspend fun approveDeviceCode(@Body approveDeviceRequest: ApproveDeviceRequest): Response<Unit>

    /**
     * POST auth/login
     * Sign in with an email and a password
     * On failure the response is deliberately generic (&#x60;INVALID_CREDENTIALS&#x60;) and must not reveal whether the email exists. After five consecutive failures a progressive delay applies and &#x60;429&#x60; is returned (US-02). 
     * Responses:
     *  - 200: Signed in.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Invalid credentials (`INVALID_CREDENTIALS`).
     *  - 429: Rate limit exceeded (`RATE_LIMITED`).
     *
     * @param loginRequest 
     * @return [AuthSession]
     */
    @POST("auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<AuthSession>

    /**
     * POST auth/logout
     * Revoke the current device&#39;s token chain
     * 
     * Responses:
     *  - 204: Signed out. The refresh chain for that device is revoked.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *
     * @param refreshRequest 
     * @return [Unit]
     */
    @POST("auth/logout")
    suspend fun logout(@Body refreshRequest: RefreshRequest): Response<Unit>

    /**
     * POST auth/device/token
     * TV activation: poll for the session (RFC 8628)
     * The television polls at the &#x60;interval&#x60; returned by &#x60;POST /auth/device/code&#x60;.  While the authorization is still pending the response is &#x60;400&#x60; with &#x60;code: AUTHORIZATION_PENDING&#x60; — this is the nominal case, not a failure, and must not be surfaced as an error. On &#x60;SLOW_DOWN&#x60; the client increases its interval by 5 seconds, per RFC 8628, and keeps polling. 
     * Responses:
     *  - 200: Approved. The `device_code` is now consumed and cannot be reused.
     *  - 400: RFC 8628 polling states, carried in `code`:  | `code` | Meaning | TV behaviour | |---|---|---| | `AUTHORIZATION_PENDING` | Not approved yet | Keep polling at `interval` | | `SLOW_DOWN` | Polling too fast | Add 5 s to `interval`, keep polling | | `ACCESS_DENIED` | The user refused | Stop, show a message | | `EXPIRED_TOKEN` | The code expired | Request a new code silently | | `DEVICE_CODE_NOT_FOUND` | Unknown `device_code` | Request a new code | 
     *
     * @param deviceTokenRequest 
     * @return [AuthSession]
     */
    @POST("auth/device/token")
    suspend fun pollDeviceToken(@Body deviceTokenRequest: DeviceTokenRequest): Response<AuthSession>

    /**
     * POST auth/refresh
     * Exchange a refresh token for a new token pair
     * Rotation: the presented refresh token is revoked and a new one is issued.  Presenting a token that was already consumed returns &#x60;401&#x60; with &#x60;REFRESH_TOKEN_REUSED&#x60; and revokes **the entire token chain for that device** — the device must sign in again.  Clients must serialise refresh calls (one in flight at a time). Two parallel refreshes at start-up revoke each other and sign the user out; this is the classic failure of this pattern (US-04). 
     * Responses:
     *  - 200: A new token pair. The previous refresh token is now revoked.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: The refresh token is unknown, expired or revoked (`REFRESH_TOKEN_INVALID`), or was already consumed (`REFRESH_TOKEN_REUSED`, which revokes the device's whole chain). 
     *
     * @param refreshRequest 
     * @return [TokenPair]
     */
    @POST("auth/refresh")
    suspend fun refreshSession(@Body refreshRequest: RefreshRequest): Response<TokenPair>

    /**
     * POST auth/register
     * Create an account with an email and a password
     * Creates the account, provisions the calling &#x60;device&#x60;, issues a session and sends a verification email. The user is signed in immediately.  Responses for an already-registered email are constant-time with respect to an unknown email: the implementation must not leak account existence through response latency (US-01). 
     * Responses:
     *  - 201: Account created; the caller is signed in.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 409: An account already exists for this email (`EMAIL_ALREADY_REGISTERED`). The client invites the user to sign in or reset their password. 
     *  - 422: The password does not meet the strength requirement (`PASSWORD_TOO_WEAK`).
     *  - 429: Rate limit exceeded (`RATE_LIMITED`).
     *
     * @param registerRequest 
     * @return [AuthSession]
     */
    @POST("auth/register")
    suspend fun register(@Body registerRequest: RegisterRequest): Response<AuthSession>

    /**
     * POST auth/device/code
     * TV activation: request a user code (RFC 8628)
     * Called by the television. Returns a short &#x60;user_code&#x60; to display, and a &#x60;device_code&#x60; the TV keeps private and polls with.  Typing an email and a password on a remote control is a punitive experience and the first abandonment point of a TV application; this flow exists to avoid it entirely.  Properties on this endpoint and on &#x60;/auth/device/token&#x60; keep their RFC 8628 names verbatim. 
     * Responses:
     *  - 201: A pending authorization was created.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 429: Rate limit exceeded (`RATE_LIMITED`).
     *
     * @param deviceCodeRequest 
     * @return [DeviceCodeResponse]
     */
    @POST("auth/device/code")
    suspend fun requestDeviceCode(@Body deviceCodeRequest: DeviceCodeRequest): Response<DeviceCodeResponse>

    /**
     * POST auth/password/forgot
     * Request a password reset email
     * Always answers &#x60;202&#x60;, whether or not the email is registered. Account enumeration through this endpoint is not possible. 
     * Responses:
     *  - 202: If the email is registered, a reset message has been sent.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 429: Rate limit exceeded (`RATE_LIMITED`).
     *
     * @param forgotPasswordRequest 
     * @return [Unit]
     */
    @POST("auth/password/forgot")
    suspend fun requestPasswordReset(@Body forgotPasswordRequest: ForgotPasswordRequest): Response<Unit>

    /**
     * POST auth/password/reset
     * Set a new password from a reset token
     * Succeeding revokes every refresh token of the account.
     * Responses:
     *  - 204: Password changed. All sessions of the account are revoked.
     *  - 400: The token is malformed or unknown (`RESET_TOKEN_INVALID`).
     *  - 410: The token has expired (`RESET_TOKEN_EXPIRED`).
     *  - 422: The new password does not meet the strength requirement (`PASSWORD_TOO_WEAK`).
     *
     * @param resetPasswordRequest 
     * @return [Unit]
     */
    @POST("auth/password/reset")
    suspend fun resetPassword(@Body resetPasswordRequest: ResetPasswordRequest): Response<Unit>

    /**
     * POST auth/oauth/google
     * Exchange a Google ID token for a Lumo session
     * The server verifies the &#x60;id_token&#x60; itself — signature, &#x60;aud&#x60;, &#x60;iss&#x60;, &#x60;exp&#x60;. It never trusts an email supplied by the client.  If an account already exists for the verified email, the Google identity is **attached to that account**. A duplicate account is never created (US-03). 
     * Responses:
     *  - 200: Signed in. The account was created or linked as needed.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: The Google ID token failed verification (`OAUTH_TOKEN_INVALID`).
     *  - 429: Rate limit exceeded (`RATE_LIMITED`).
     *
     * @param googleSignInRequest 
     * @return [AuthSession]
     */
    @POST("auth/oauth/google")
    suspend fun signInWithGoogle(@Body googleSignInRequest: GoogleSignInRequest): Response<AuthSession>

    /**
     * GET auth/verify-email
     * Confirm an email address from the link sent by email
     * 
     * Responses:
     *  - 204: Email verified. `email_verified_at` is now set.
     *  - 400: The token is malformed or unknown (`VERIFICATION_TOKEN_INVALID`).
     *  - 410: The token has expired (`VERIFICATION_TOKEN_EXPIRED`).
     *
     * @param token Single-use verification token from the email link.
     * @return [Unit]
     */
    @GET("auth/verify-email")
    suspend fun verifyEmail(@Query("token") token: kotlin.String): Response<Unit>

}
