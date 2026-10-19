# ADR 0014 — Account deletion (S16B-02)

- **Date** : 2026-10-19
- **Status** : Proposed (to be accepted by Hamza)
- **Deciders** : Tech Lead (proposes), Hamza (accepts)
- **Supersedes** : none

## Context

Google Play requires every app that creates accounts to let the user delete the account **from
inside the app** and to publish a **web page** explaining how (sprint 16-bis, S16B-02). Until now
the contract only had `DELETE /me/devices/{id}`. Three questions had to be settled before writing an
endpoint: how the request is confirmed, what "deleted" covers, and what happens to the account's
other signed-in devices, whose 15-minute access tokens stay cryptographically valid.

## Decision

### 1. `POST /me/delete`, confirmed by the account password

An action endpoint rather than `DELETE /me` with a body: a body on `DELETE` has no defined meaning in
HTTP and is dropped by some intermediaries and client libraries (Retrofit refuses one on `@DELETE`).

The password is required although the call is authenticated: an unlocked phone is a valid session
and must not be enough to erase the account. A wrong password is **`403 INVALID_CREDENTIALS`**, never
`401`, which every client treats as "refresh or sign out". Attempts are throttled per account at the
sign-in pace (`RATE_LIMITED`). An SSO-only account (no password) sets one through the password reset
first; no client offers Google sign-in since 0.2.0, so this is a corner case, not a flow.

### 2. Immediate, complete, irreversible — by cascade

One `DELETE FROM "user"` in one transaction. Every table holding a `user_id` or a `source_id`
already declares `ON DELETE CASCADE` (sources and their encrypted credentials, the whole ingested
catalogue, favourites, watchlist, progress, recent channels, devices, refresh tokens, TV
activations, user tokens, SSO links, entitlement). No soft delete, no grace period, no copy kept
aside: the same email can register again as a new, empty account. Database backups age out on their
own retention, which the privacy policy states.

`AccountDeletionIntegrationTest` sweeps `information_schema` for every `user_id` / `source_id` column
rather than listing tables, so a future table that points at the account another way shows up as a
survivor, and one without the cascade fails the deletion itself.

### 3. Other devices are signed out at their next call

The JWT converter now rejects a token whose subject no longer exists (`401 UNAUTHENTICATED`): one
primary-key probe per authenticated request. Without it, another device of a deleted account would
keep writing for up to fifteen minutes on behalf of a user who no longer exists. Its refresh token is
gone with the device row (`401 REFRESH_TOKEN_INVALID`), so each client ends the session through its
existing path. Device revocation (`DELETE /me/devices/{id}`) keeps its current behaviour.

## Consequences

**Positive.** One endpoint covers the Play requirement on the three surfaces; nothing to schedule,
no purge job, no state "pending deletion" for clients to render.

**Negative.** One extra indexed query per authenticated request. A deletion cannot be undone, even by
an operator, except by restoring a whole backup. Stripe customers (not live in 0.4.0) are not deleted
at Stripe; the day billing ships, the deletion has to cancel and detach them first.

## Alternatives considered

- **Soft delete with a 30-day grace period.** Rejected for 0.4.0: every query would need a filter,
  and "deleted but still there" is harder to explain honestly on the public page.
- **Recent sign-in instead of a password** (re-authenticate within N minutes). Rejected: needs a new
  claim and a new flow on three clients for the same guarantee.
- **Revoking access tokens through a deny-list.** Rejected: the existence check is simpler and
  cannot be forgotten by a code path.
