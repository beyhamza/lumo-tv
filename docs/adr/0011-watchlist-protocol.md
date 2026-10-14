# ADR 0011 — Watchlist protocol (C2)

- **Date** : 2026-10-08
- **Status** : Proposed
- **Deciders** : Tech Lead (proposes), Hamza (accepts)
- **Supersedes** : none

## Context

`US-022` adds a **watchlist**: films and series an account saves deliberately, shared
across the web, mobile and TV clients. The contract today models channel
**favourites** and their groups, neither of which represents a film or a series, and
no watchlist resource exists (`grep -i watchlist packages/contracts/openapi.yaml` →
zero hits). The product rules are settled in
[`watchlist-states.md`](../../design/0.2.0/watchlist-states.md) (WL-01→17) and
[`favorite-organization-cases.md`](../../design/0.2.0/favorite-organization-cases.md)
(FO-01→12); what is missing is the **protocol** those rules require.

Two properties of the current contract force this ADR rather than an improvisation:

1. A catalogue record **can disappear** after a refresh (provider removes a film, a
   source is deleted). A watchlist that stores only a foreign key to that record
   cannot render the card the product has decided to keep.
2. `updateFavorite` moves **one** favourite, with no idempotency key and no batch
   operation. Replaying a write is indistinguishable from a new intention, which is
   exactly the guarantee `WL-10/11/12` require and `S11-00` refuses to fake.

Anything below that changes `openapi.yaml` is a **structuring contract change**
(`AGENTS.md` §3) and needs this ADR accepted before a line of `S11-01` code.

## Decision

### 1. The watchlist is its own resource, not a reuse of favourites or progress

A new account-scoped resource (`/v1/me/watchlist`), covering **films and series**
only. It does not reuse channel favourites, and it does not reuse playback progress:
progress is a position, the watchlist is a selection, and `WL-13` keeps them
independent.

### 2. Identity is declared by the provider, never by the title

An item is keyed by `(account, source, content_type, provider_content_id)`. A
content that **returns with the same recognised identity** resumes its card and its
rank without a new add (`WL-15`); a **different** item sharing the title is never
merged (`WL-16`). No fuzzy matching, no title key.

### 3. A disappeared item keeps a display snapshot; the sheet is not required

The item stores the minimum needed to render its card without the catalogue record:
`content_type` and a known `title`, plus whatever reference the surface needs for a
placeholder poster. Marking **`unavailable`** happens only after a **confirmed**
refresh says the item is gone (`WL-01`, `WL-02`); a fetch error does not
(`WL-04`). Removal of an unavailable item is addressed by the **watchlist item
identity**, so it works without a sheet (`WL-03`). A **confirmed source deletion**
cascades to removal of the item — the card disappears, it does not become
`unavailable` (`WL-05`, US-024).

### 4. Offline is read-only; there is no deferred write queue

Without a connection the list already held may be consulted with an explicit offline
state; add and remove are unavailable; **no local write is queued and replayed**
(`WL-06`, `WL-07`). On reconnect the shared state is **re-read before mutations are
re-enabled** (`WL-08`). The watchlist promises neither offline playback nor durable
offline persistence; any future cache follows the applicable ADR, not this one.

### 5. The server is authoritative for order and for acceptance

A new item is ordered by a server-assigned `added_at`, most recent first. Between two
**new intentions**, the last action **accepted by the server** wins, decided by the
server's acceptance order and **never by a client clock** (`WL-09`, `FO-11/12`). An
unknown result is not success: the client re-reads before retrying and never
overwrites a newer accepted action.

### 6. Idempotency: a client-generated key per intention — the one contract evolution

Write operations carry a client-generated **idempotency key** (one per user
intention). The server records `(account, key) → outcome` for a bounded retention
window; a replay of the same key is a **no-op** that returns the stored outcome and
changes neither membership nor `added_at`/rank (`WL-11`). A different key is a new
intention and is ordered per ruling 5 (`WL-12`). This is the mechanism that makes
"a technical retry is not a new intention" true; without it the guarantee is not
expressible.

### 7. No atomic permutation, no batch endpoint

The filtered reorder (`FO-01/02`) stays a composition of **unit** moves; the pure
`visibleSlot → fullIndex` mapping is the client's job. Product does not promise an
atomic permutation (`US-022`), so the protocol does not add a batch endpoint. The
guarantee is the one already stated in
[`S11-00-guarantees.md`](../../backlog/sprint-11/S11-00-guarantees.md) G1/G2: a
partial failure leaves a valid but partially permuted group, the successful moves
are kept, the state is re-read, and success is never announced.

### 8. The three clients are regenerated, never hand-patched

The contract change is followed by `openapi-generator` for Android and
`openapi-typescript` for web as usual (`ADR 0001`). No client stores watchlist state
that the server owns.

## Consequences

**Positive.** The product rules become testable statements rather than adjectives.
An unavailable card is renderable by construction. Retries are safe by construction.
The watchlist cannot be silently confused with favourites or progress.

**Negative.** One new resource, one new storage model, one migration — heavier than
reusing favourites. The idempotency table needs a retention policy and a purge.
Clients must generate and persist an intention key across a retry, which is real
client work, not a wrapper.

**Open, not decided here.** The retention window and storage shape of idempotency
records are `S11-01` implementation details, bounded by this ruling (bounded
retention, no change to membership on replay). Whether an unavailable item's poster
reference is a stored key or a re-resolution attempt is left to `S11-01`.

## Alternatives considered

**Reuse channel favourites.** Rejected: `Favorite` references a channel, and
hijacking it would make one model mean two things — the exact drift `ADR 0001`
exists to prevent.

**Store only the catalogue foreign key.** Rejected: a disappeared record would take
the card with it, contradicting `WL-01/02` and making `Indisponible` unrenderable.

**Client-side generated watchlist.** Rejected: the list is shared across devices
(`US-022`), so the server owns membership; a client-side list has no shared state to
converge on.

**Batch/atomic permutation endpoint now.** Rejected for this cycle: product does not
promise atomicity, and an endpoint added "to be safe" is a contract surface nobody
has recette'd. Reopen with an ADR if a partial permutation ever causes visible loss.

**Order by client clock.** Rejected: device clocks disagree, and the product rule is
explicitly server-acceptance order (`WL-09`).

**Idempotency via a natural key (account + content).** Rejected as the *general*
mechanism: it would make a deliberate re-add after removal indistinguishable from a
retry, which is a different intention. The key is per intention, not per item.
