# ADR 0008 — The Android applications permit cleartext to the user's server, and only there

- **Date** : 2026-08-27
- **Status** : Accepted

## Context

[ADR 0007](./0007-web-playback-direct-only.md) accepted that web playback works for a
minority of our users, and its consolation was explicit: *"the phone and the
television applications, which have neither constraint"*. `architecture.md` §1 rests
on the same sentence — the media never transits our infrastructure, because each
client opens the stream at the user's own IPTV server.

That is true of the browser's constraints. It is not currently true of Android's.

Neither `app-mobile/AndroidManifest.xml` nor `app-tv/AndroidManifest.xml` declares
`usesCleartextTraffic` or a `networkSecurityConfig`. Since `targetSdk` 28 the platform
default is to **refuse cleartext HTTP**, and both applications target 37. Meanwhile:

| What is fetched | Where from | Usually served over |
|---|---|---|
| The stream, at playback | The user's Xtream panel or M3U host | `http`, on a high port |
| The channel logo (`tvg-logo`) | The same host | `http` |
| The XMLTV guide | The same host | `http` |
| Everything else | `api.lumo.tv` | `https`, ours, always |

So the applications that ADR 0007 names as the way out cannot, as shipped, open the
majority of our users' sources. Worse, the failure is silent in exactly the wrong way:
Media3 surfaces a `SecurityException` deep in a load error, and the player shows a
black rectangle rather than a sentence. This was found while writing S2-10, where the
same fact shows up harmlessly as a logo that will not load.

The question is not *whether* to permit cleartext — refusing it means refusing the
product's central promise on its main platform. The question is **how narrowly**.

## Decision

**A `networkSecurityConfig` whose base configuration permits cleartext, with our own
API pinned to HTTPS by an explicit domain rule.**

```
base-config          cleartextTrafficPermitted="true"    ← the user's servers
domain-config        api.lumo.tv, cleartextTrafficPermitted="false", includeSubdomains
```

The base configuration has to be permissive because **we cannot know the hosts in
advance**. A user's provider is a hostname they type into a form; there is no list to
allow, and there never will be. That is the whole shape of this product — the user
brings their own source (AGENTS.md §1).

The domain rule is what keeps that from being a blanket. Our API carries the session
tokens and every piece of account data, and it is the one host whose address we *do*
know at build time. Downgrading it must be impossible, not merely unlikely, so it is
refused by policy rather than by everybody remembering to type `https`.

One file, shared by both applications, in `core:network` — the module that already
owns how this product talks to anything.

## Rules

1. **Never `android:usesCleartextTraffic="true"`.** It is the same permission with no
   exception for our own API, which is precisely the part that must not be
   downgradable.
2. **The domain list is ours, and is not a convenience list.** `api.lumo.tv` is on it
   because it is ours. A provider's host never goes on it, in either direction: we do
   not know them, and adding one would be pretending we do.
3. **Cleartext is for media and metadata only.** No credential of ours ever crosses
   it. The Xtream password travels to `api.lumo.tv` over TLS and is stored encrypted
   server-side; the stream URL the panel issues may embed the user's own provider
   credentials, and that is the provider's design, not a choice we get to make.
4. **The stream URL still never reaches a log** (AGENTS.md §5, `PlaybackTarget`).
   Permitting cleartext changes nothing about that.

## Consequences

**What this buys.** The phone and the television play what the browser cannot, which
is the difference ADR 0007 promised and the reason the applications exist. Channel
logos load. The guide loads.

**What it costs, stated plainly.** A stream fetched over `http` can be observed and
altered by anyone on the path between the device and the user's provider. That is
already true of every other IPTV client on the market and of the provider's own
delivery — the panels are the ones serving in the clear — but it is now true of ours
too, deliberately, and it should be said in the store listing rather than discovered.

**What it does not cost.** Nothing of ours moves. `api.lumo.tv` is unreachable over
cleartext from these applications, by policy, and an attempt is refused by the
platform rather than by our code.

**What still fails.** A provider serving over `https` with an invalid or self-signed
certificate. Cleartext and certificate validation are different rules, and this ADR
relaxes only the first — the second protects a real user against a real
man-in-the-middle, and there is no version of "just trust it" worth having.

**The one thing to watch.** This makes it *possible* for a future piece of code to
call something of ours over `http` and have it silently work in a debug build against
a local server. The domain rule covers `api.lumo.tv`; a development base URL is a
different host. That is a trade taken knowingly: local development against
`http://10.0.2.2:8080` is how the emulator reaches the host machine, and forbidding it
would mean a second configuration for debug builds — one more thing to get wrong, in
the direction where getting it wrong is harmless.
