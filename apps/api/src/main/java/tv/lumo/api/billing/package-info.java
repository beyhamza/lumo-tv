/**
 * Stripe subscriptions and entitlements.
 *
 * <p><b>Not implemented.</b> {@code GET /me/entitlement} is defined in the
 * contract but is outside sprint 1, which contains no payment story.
 *
 * <p>When it is built, this package holds the Stripe webhook handler and it is
 * the ONLY writer of the {@code entitlement} table, which is the single source of
 * truth for access rights (ADR 0003). No client ever asks a store whether a user
 * is premium.
 */
package tv.lumo.api.billing;
