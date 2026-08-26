package tv.lumo.android.network.generated.api

import tv.lumo.android.network.generated.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import tv.lumo.android.network.generated.model.BillingSession
import tv.lumo.android.network.generated.model.CreateCheckoutSessionRequest
import tv.lumo.android.network.generated.model.CreatePortalSessionRequest
import tv.lumo.android.network.generated.model.Problem

interface BillingApi {
    /**
     * POST billing/checkout-session
     * Open a Stripe Checkout session for the paid plan
     * Returns a URL to redirect to. **Nothing is granted here.** The entitlement changes only when Stripe&#39;s webhook writes it, and clients keep reading &#x60;GET /me/entitlement&#x60; afterwards (ADR 0003) — a client that assumes premium because the redirect succeeded will be wrong for every abandoned checkout.  **The success and cancel URLs are not accepted from the client.** They are built server-side from configuration. A caller-supplied return URL is an open redirect wearing a billing costume, and this project already guards the same hole on the sign-in &#x60;next&#x60; parameter. The optional &#x60;locale&#x60; is the only thing the caller gets to influence, and it only picks the language Stripe renders in. 
     * Responses:
     *  - 200: A Checkout session was created. Redirect the user to `url`.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 409: The account already holds an active paid entitlement (`ALREADY_SUBSCRIBED`). The client sends the user to the portal instead of opening a second subscription. 
     *  - 429: Rate limit exceeded (`RATE_LIMITED`).
     *
     * @param createCheckoutSessionRequest 
     * @return [BillingSession]
     */
    @POST("billing/checkout-session")
    suspend fun createCheckoutSession(@Body createCheckoutSessionRequest: CreateCheckoutSessionRequest): Response<BillingSession>

    /**
     * POST billing/portal-session
     * Open the Stripe customer portal
     * Changing a payment method, downloading an invoice, cancelling — the portal covers all three. That is the whole reason it is here: none of those screens has to exist in Lumo, in three applications, in two languages.  Return URL built server-side, for the reason given on &#x60;POST /billing/checkout-session&#x60;. 
     * Responses:
     *  - 200: A portal session was created. Redirect the user to `url`.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 404: This account has never been billed, so it has no customer record to open a portal on (`BILLING_CUSTOMER_NOT_FOUND`). A `FREE` user who never subscribed is the ordinary case; the client offers checkout, not the portal. 
     *  - 429: Rate limit exceeded (`RATE_LIMITED`).
     *
     * @param createPortalSessionRequest  (optional)
     * @return [BillingSession]
     */
    @POST("billing/portal-session")
    suspend fun createPortalSession(@Body createPortalSessionRequest: CreatePortalSessionRequest? = null): Response<BillingSession>

}
