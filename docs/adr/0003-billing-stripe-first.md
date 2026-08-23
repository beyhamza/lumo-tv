# ADR 0003 — Stripe first, server-side entitlements

- **Date** : 2026-08-22
- **Status** : Accepted

## Context

Since 30 June 2026, in the EEA, UK and US, Google Play separates its service fee from
its billing fee. The service fee starts at 10% on the first $1M of annual earnings
regardless of payment route, and that 10% also applies to auto-renewing subscriptions.
Using Google Play Billing adds a 5% billing fee. Effective rate on a subscription:
roughly 15% via Play Billing, roughly 12% via Stripe once processing fees are counted.

The margin difference is therefore small, and three product constraints matter more:

1. Two Play listings (see ADR 0004) mean a Play Billing purchase on one listing is not
   recognised by the other.
2. Entering card details with a remote control is a severe UX penalty on Android TV.
3. Stripe makes us merchant of record, so EU VAT (OSS filings, per-country rates)
   becomes our operational responsibility. Google absorbs it under Play Billing.

## Decision

**v1: Stripe on lumo.tv only.** Applications carry no purchase flow; they sign in and
read their entitlement from the API.

`entitlement` in PostgreSQL is the single source of truth. Clients call
`GET /v1/me/entitlement`. No client ever asks a store whether the user is premium.

## Consequences

One purchase path to build, one to test, one to support. Both Android listings share
one entitlement. The TV purchase path is the device-code flow, which we need anyway.

In-app conversion is lower than a native store flow. VAT handling is on us — a
merchant-of-record provider (Paddle, Lemon Squeezy, ~5%) is a valid escape hatch if the
administrative load outweighs the margin.

Adding Play Billing in v2 requires only a new writer into `entitlement`, fed by
Real-time Developer Notifications. No client-side rework.

*Fee structure and tax treatment stated here are as understood on 2026-08-22 and are
not legal or tax advice. Verify against Play Console policy and an accountant before
launch.*
