# ADR 0013 — Open Picture-in-Picture and Chromecast (0.4.0)

- **Date** : 2026-10-18
- **Status** : Accepted (2026-10-18, Hamza)
- **Deciders** : Tech Lead (proposes), Hamza (accepts)
- **Supersedes** : none — amends the v1 out-of-scope list of `AGENTS.md` §6 for these two items only

## Context

`AGENTS.md` §6 lists Chromecast and Picture-in-Picture as v2, out of scope. On 18 October 2026 Hamza
chose "lecteur avancé" for the next version (`docs/roadmap/0.4.0/`). Opening them is a scope change,
hence this ADR. Two constraints shape it:

- **Playback is direct, never relayed** (ADR 0007). A Cast receiver plays the provider's URL itself;
  Lumo does not proxy, transcode or re-host a stream to make a device accept it.
- **Access rights are computed server-side** (`AGENTS.md`, rule 3). Neither feature is gated by plan in
  0.4.0 (the product is free); if one ever is, the server says so through the entitlement.

## Decision

1. **Picture-in-Picture** is in scope on **Android mobile** (platform PiP: the player keeps playing in
   a floating window when the user leaves the app, with play/pause; S10B-03's "pause in background"
   applies only when PiP is not entered) and on the **web** (the browser Picture-in-Picture API on the
   film, episode and channel players). Not on Android TV.
2. **Chromecast** is in scope as a **sender** on **Android mobile** (Google Cast SDK) and the **web**
   (Cast sender in Chromium browsers), using Google's **Default Media Receiver** — no custom receiver app
   in 0.4.0. The sender hands the receiver the playback URL the API already returns
   (`GET /…/playback`), plus title and poster; position, progress saving and "Continue watching" keep
   working while casting (the sender reports watched segments only while the receiver is actually
   playing — ADR 0012).
3. **A stream the receiver cannot play is named**, never hidden behind a generic error: format, `http`
   or CORS refusal → "Ce flux ne peut pas être envoyé sur ce Chromecast", playback stays on the phone.
   A technical spike (`S15-00`) measures what the Default Media Receiver accepts before S16 commits.
4. **No contract change** is expected: the playback endpoints already return what a sender needs. If
   the spike shows one is needed (e.g. a content type hint), it goes through `openapi.yaml` first.
5. One new dependency on Android (`play-services-cast-framework` + `mediarouter`), justified here as
   required by `AGENTS.md` §8 for heavy dependencies; none on the web (the Cast sender script is loaded
   from Google at runtime only on the player pages, never on marketing pages).

## Consequences

**Positive.** Two features users expect from a video app, delivered without touching the
no-relay rule or the server's ownership of rights.

**Negative.** Cast depends on Google Play services and on what each provider's streams allow; some
users will see "can't be cast" for their source. The Android app gains a Google dependency. PiP adds
lifecycle states every player screen must handle (enter, exit, close the window = stop).

## Alternatives considered

**Custom Cast receiver** (our own receiver web app). Rejected for 0.4.0: hosting, registration and a
second player to maintain; reconsider if the spike shows the default receiver refuses most streams.

**Relaying streams to make them castable.** Rejected: contradicts ADR 0007 and puts Lumo in the path of
content it must never serve.

**PiP on Android TV.** Rejected: the TV is the destination screen; TV PiP is rare and unsupported on
many devices.
