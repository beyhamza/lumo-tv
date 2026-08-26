/**
 * Subscriptions and entitlements.
 *
 * <p>This package is the <b>only</b> writer of the {@code entitlement} table,
 * which is the single source of truth for access rights (ADR 0003). No client
 * ever asks a store whether a user is premium, and no service outside this
 * package decides what a plan allows: {@code SourceService} and
 * {@code AccountService} ask {@link tv.lumo.api.billing.EntitlementService}
 * whether there is room, and it answers from {@code lumo.plans.*} — the one place
 * in the whole product where "FREE means one source" is written down.
 *
 * <p>Implemented: {@code GET /me/entitlement} (served by
 * {@code AccountController}, which owns the {@code account} tag),
 * {@code POST /billing/checkout-session} and
 * {@code POST /billing/portal-session}.
 *
 * <p><b>Not implemented: the webhook.</b> A completed payment therefore changes
 * nothing yet — the checkout opens, the customer is created, and {@code plan}
 * stays {@code FREE}. The endpoint that would receive the provider's callback is
 * absent from {@code packages/contracts/openapi.yaml}, and AGENTS.md §3 says a
 * need the contract does not cover is escalated rather than invented. It is
 * recorded in docs/design/api-gaps.md as a decision to take, not as an oversight.
 *
 * <p>A deployment with no provider configured is a supported state: both billing
 * endpoints answer 503 and everything else works.
 */
package tv.lumo.api.billing;
