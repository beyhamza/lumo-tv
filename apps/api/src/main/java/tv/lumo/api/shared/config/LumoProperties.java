package tv.lumo.api.shared.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Everything under the {@code lumo.*} prefix, bound and validated at start-up.
 *
 * <p>Records, so configuration is immutable once bound. {@code @Validated} makes
 * a missing secret a start-up failure with a readable message rather than a
 * {@code NullPointerException} on the first request that needs it.
 *
 * @param jwt        access-token signing and lifetime
 * @param refresh    refresh-token lifetime
 * @param encryption master key for source credentials
 * @param deviceCode RFC 8628 TV activation timings
 * @param web        public web surface, for links sent by email or encoded in a QR code
 * @param cors       browser origins allowed to call this API
 * @param ingest     explicit backpressure for outbound calls (ADR 0005 §1)
 * @param rateLimit  abuse limits on the authentication surface
 * @param autoSync   how often the server refreshes sources on the user's behalf
 * @param plans      what each subscription tier allows — the single place those
 *                   numbers exist anywhere in the product
 */
@ConfigurationProperties(prefix = "lumo")
@Validated
public record LumoProperties(
        @NotNull Jwt jwt,
        @NotNull Refresh refresh,
        @NotNull Encryption encryption,
        @NotNull DeviceCode deviceCode,
        @NotNull Web web,
        @NotNull Cors cors,
        @NotNull Ingest ingest,
        @NotNull RateLimit rateLimit,
        @NotNull AutoSync autoSync,
        @NotNull Plans plans,
        @NotNull Billing billing
) {

    public record Jwt(
            /** HMAC signing key, at least 32 bytes. No default: see application.yml. */
            @NotBlank String secret,
            @NotNull Duration accessTtl,
            @NotBlank String issuer
    ) {}

    public record Refresh(@NotNull Duration ttl) {}

    public record Encryption(
            /** Base64, 32 bytes. Wraps the per-credential data keys. */
            @NotBlank String masterKey
    ) {}

    public record DeviceCode(@NotNull Duration ttl, @NotNull Duration pollInterval) {}

    public record Web(@NotBlank String baseUrl) {}

    public record Cors(@DefaultValue("") List<String> allowedOrigins) {}

    public record Ingest(
            /**
             * Concurrent outbound requests allowed to a SINGLE destination host.
             * A product safeguard, not a performance knob: virtual threads removed
             * the ceiling a bounded pool used to give for free, and without this a
             * burst of syncs gets the USER's own IPTV account throttled or banned.
             */
            @Positive int maxConcurrentPerHost,
            @Positive int maxConcurrentSyncs,
            @NotNull Duration httpTimeout,
            @Positive int maxPayloadMb
    ) {
        public long maxPayloadBytes() {
            return (long) maxPayloadMb * 1024L * 1024L;
        }
    }

    public record RateLimit(
            @Positive int authAttemptsPerMinute,
            @Positive int deviceApproveAttemptsPerMinute
    ) {}

    /**
     * Server-side re-synchronisation of sources that asked for it.
     *
     * @param enabled       off in tests and in any deployment that would rather
     *                      not reach the users' panels on a timer
     * @param sweepInterval how often the sweep looks for due sources. Also read
     *                      directly by {@code @Scheduled} — same key, so the two
     *                      cannot disagree
     * @param everyHours    how stale a catalogue must be before it is refreshed
     * @param batchSize     how many sources one sweep may start. The bound that
     *                      keeps "we refresh it for you" from becoming a thousand
     *                      simultaneous connections to a thousand panels
     */
    public record AutoSync(
            @DefaultValue("true") boolean enabled,
            @NotNull Duration sweepInterval,
            @Positive int everyHours,
            @Positive int batchSize
    ) {}

    /**
     * What each tier allows.
     *
     * <p><b>These numbers exist here and nowhere else.</b> Written into the web,
     * the phone and the television they would be three copies of an access right
     * computed client-side, which AGENTS.md §1 forbids outright. Clients read them
     * off {@code GET /me/entitlement}; the day the free plan allows two sources,
     * one line changes here and three applications follow with no release.
     */
    public record Plans(@NotNull Limits free, @NotNull Limits premium) {}

    /**
     * @param maxSources null means unlimited, not unknown — the same meaning the
     *                   contract gives the property
     * @param maxDevices same rule
     */
    public record Limits(Integer maxSources, Integer maxDevices) {}

    /**
     * Stripe.
     *
     * <p><b>Absent by default, and that is a supported state.</b> Unlike the JWT
     * and encryption secrets, a missing key here does not stop the application:
     * every other endpoint works without a payment provider, and refusing to boot
     * a development environment because nobody has a Stripe account would be
     * absurd. The two billing endpoints answer 503 instead — see
     * {@code BillingController}.
     *
     * @param secretKey   {@code sk_...}. Never logged, never returned, never
     *                    committed
     * @param priceId     the recurring price the paid plan buys
     * @param apiBaseUrl  overridable so a test can point at a local double
     *                    instead of the real Stripe
     * @param trialDays   0 for no trial. What puts an entitlement in
     *                    {@code TRIALING}
     * @param successPath where Stripe returns after a successful checkout,
     *                    resolved against {@code lumo.web.base-url}. <b>Built
     *                    server-side and never accepted from the caller</b>: a
     *                    client-supplied return URL is an open redirect wearing a
     *                    billing costume
     * @param cancelPath  same, for an abandoned checkout
     * @param returnPath  same, for the customer portal
     */
    public record Billing(
            @DefaultValue("") String secretKey,
            @DefaultValue("") String priceId,
            @DefaultValue("https://api.stripe.com") String apiBaseUrl,
            @DefaultValue("0") int trialDays,
            @DefaultValue("/app/subscription?checkout=success") String successPath,
            @DefaultValue("/app/subscription?checkout=cancelled") String cancelPath,
            @DefaultValue("/app/subscription") String returnPath
    ) {

        /** Whether this deployment can talk to a payment provider at all. */
        public boolean configured() {
            return !secretKey.isBlank() && !priceId.isBlank();
        }
    }
}
