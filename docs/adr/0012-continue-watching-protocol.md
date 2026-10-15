# ADR 0012 — Continue watching protocol (C3)

- **Date** : 2026-10-15
- **Status** : Accepted (2026-10-15, Hamza)
- **Deciders** : Tech Lead (proposes), Hamza (accepts)
- **Supersedes** : none

## Context

`US-019` (sprint 12) turns saved positions into a shared "Continue watching" rail.
The product rules are settled in
[`continue-watching-cases.md`](../design/0.2.0/continue-watching-cases.md)
(CW-01→33). Today `playback_progress` stores one position per item, written by
`PUT /me/progress` with no notion of time actually watched, of a playback session,
or of a card the user hid. Every client derives the rail itself from positions.

Three rules cannot be expressed with that: a card appears only after **30 seconds
actually watched, summed across devices, simultaneous seconds counted once**
(CW-01→05, CW-17, CW-29); the **most recent playback wins** the position even when
less advanced, and a late save from a reconnecting device must not win (CW-30,
CW-32); a **hidden card stays hidden** for sessions already running and reappears on
the next real start (CW-10, CW-18, CW-22, CW-31). A retry must never add watched
time (CW-33).

## Decision

### 1. Watched time is a union of wall-clock intervals, kept by the server

A save may carry `watched_segments`: the intervals, in wall-clock time, during which
the player was actually playing (not paused, not buffering, not seeking) since the
previous save. The server keeps, per item, the **union** of all segments received
from every device and derives `watched_ms` from it.

- CW-17: segments from two devices add up.
- CW-29: two devices playing during the same 20 seconds overlap; the union counts 20.
- CW-04: a seek produces no segment, so jumping 20 minutes adds nothing.
- CW-33: resending a segment adds nothing — a union is idempotent by construction,
  so no idempotency key is needed for progress.

Segments are clamped to the server's clock (none in the future) and to a bounded
number per request. Device clocks may drift by seconds; the error is bounded by that
drift and accepted.

### 2. The most recent observation wins the position

A save carries `observed_at`, the device time at which the position was read. The
server keeps the position with the **latest `observed_at`**, not the latest received
(CW-30, CW-32). A save without `observed_at` (older clients) is stamped with the
receive time.

### 3. A hidden card stays hidden until a new real start

`POST /me/progress/hide` hides an item from the rail (`hidden_at = now`); progress is
kept (CW-10). For an episode, the whole series is hidden: the rail shows one card per
series. A save carries `session_started_at`, the time playback really started on that
device. The item reappears only when a save arrives whose `session_started_at` is
**after** `hidden_at` (CW-18, CW-22) — a session already running when the card was
hidden keeps saving without bringing it back (CW-31).

### 4. The server says whether an item is in the rail

`PlaybackProgress` gains `watched_ms`, `hidden` and `in_continue`. `in_continue` is
true when the item is not hidden, not finished (position > 95 % of a known duration,
CW-06/07) and either has 30 s watched or was restarted after being hidden. Clients
group episodes by series and order by most recent; they no longer compute
eligibility. One rule, written once (AGENTS.md: rules that three clients would copy
belong on the server).

### 5. Additive contract change

All new request fields are optional; `PUT /me/progress` keeps working for a client
that sends none of them (no watched time, receive-time recency). Clients are
regenerated, never hand-patched (ADR 0001).

## Consequences

**Positive.** Every CW rule above becomes a server-side, testable statement. Retries
are harmless by construction. The rail is the same on all surfaces.

**Negative.** A new storage for intervals (bounded: merged on write, so a few rows per
item). Players must measure effective playback, which is real client work (S12-02).
Device clock drift slightly blurs CW-29 and CW-32; accepted.

## Alternatives considered

**Client-side eligibility** (today). Rejected: three copies of the 30 s rule and of the
95 % rule, and no way to count simultaneous seconds once.

**Summing watched durations instead of intervals.** Rejected: two devices playing
together would count 40 s (CW-29) and a resent save would double count (CW-33).

**Idempotency key per save.** Rejected as unnecessary: the interval union is already
idempotent.
