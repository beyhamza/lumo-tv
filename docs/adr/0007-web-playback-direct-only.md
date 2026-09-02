# ADR 0007 — Web playback goes direct, or not at all

- **Date** : 2026-08-26
- **Status** : Accepted

## Context

`lumo.tv` is getting a channel list and a player (sprint 3, US-11). The applications
already have one on the roadmap, and for them the question does not arise: an Android
player opens the stream straight at the user's own IPTV server, which is what ADR-free
architecture §1 calls the capital point — *the media never transits our
infrastructure*. That single sentence sets our infrastructure cost (low) and our
posture (we are not a broadcaster).

A browser is not an Android application, and three constraints collide:

| Constraint | Effect |
|---|---|
| A page served over `https://` cannot load `http://` sub-resources | **Hard block**, no override, no user-facing bypass |
| `hls.js` fetches manifest and segments over XHR | Requires `Access-Control-Allow-Origin` from the panel |
| Safari plays HLS natively through `<video src>` | No CORS needed — but still no `http://` |

Most Xtream panels serve plain HTTP on a high port with no CORS header. So a browser
that respects the capital point can play **a minority of our users' sources**: those
whose provider serves HTTPS, plus HTTPS M3U playlists pointing at ordinary CDNs.

This is not a bug to be fixed by better client code. It is the browser security model,
and the only way around it is to put a server of ours between the panel and the page.

## Decision

**Option A: the browser opens the stream directly, and says so plainly when it
cannot.**

- `GET /channels/{id}/playback` is called from a same-origin Route Handler at the
  moment playback starts, never rendered into HTML. The stream URL carries the user's
  Xtream credentials and must not sit in an attribute, a hydration payload or a
  serialised server state.
- `hls.js` where MSE exists, native `<video src>` on Safari.
- When the browser blocks the stream — mixed content, missing CORS — the player says
  **what happened and what still works**: the phone and the television applications,
  which have neither constraint.

**We explicitly refuse to relay the stream through our own servers.** A same-origin
proxy in Next.js or in `lumo-api` would make every one of these cases work. It would
also:

- move our infrastructure cost from "metadata" to "our users' video bandwidth", which
  is a different business;
- change what we are. Serving the bytes of a stream we do not own is rebroadcasting,
  whatever the code calls it, and "we are not a broadcaster" stops being true the day
  we ship it.

That refusal is the substance of this ADR. Nothing else in it is hard to guess, and a
proxy is the tempting shortcut that arrives as "just a small route handler" in an
unrelated pull request.

## Consequences

Web playback works for some users and not others, and which group someone is in
depends on their provider, not on anything we control. The failure must therefore be
**named, not silent**: a black `<video>` with no message is what makes people conclude
the product is broken when it is the panel refusing. That is a dedicated sprint task
(S3-11), sized accordingly, and it is the part of the feature that decides whether it
is usable or mysterious.

A stream that plays through a browser's own HLS support and fails through `hls.js` is
expected — native playback needs no CORS, MSE always does. The diagnostic has to
account for that rather than report a generic failure, or the next bug report is "it
works on my phone".

**Amended after implementation.** The table above says Safari plays HLS natively, and
it does — but the player does not take that path on desktop Safari, and the reason is
worth recording. Choosing native from `canPlayType("application/vnd.apple.mpegurl")`
is wrong: Chromium answers `"maybe"` to it and then plays nothing, fetching every
segment, decoding none, and raising no error — a black rectangle that ends only when
the player's own give-up clock does. So the player uses MSE wherever MSE exists and
falls back to native only where it does not. The CORS-free path therefore survives on
browsers without MSE — iOS Safari among them — and desktop Safari needs CORS like
Chrome. That narrows who this option works for, which is the sort of thing this ADR
exists to say out loud.

**Amended a second time, and this one is not about the network.** Everything above
weighs whether the browser can *reach* the stream. It can reach a stream and still be
unable to play half of it: a media element decodes the picture and the sound
separately, and Chromium ships no **AC-3 / E-AC-3** decoder — Dolby is licensed and
Chromium does not pay it. The H.264 picture then plays perfectly while the browser
puts its own volume control in the muted state, which is the cruellest shape this
failure takes: somebody presses the mute button and nothing happens, because there is
nothing to unmute. Measured on a real catalogue by sampling six series at random out
of 49 437: **roughly a third of the episodes carry Dolby Digital as their only audio
track.** That is a third of a catalogue that plays silently in a browser and correctly
in the applications, and it is a larger group than mixed content on some panels.

It gets a named sentence like the rest. Reading it takes three browser-specific
properties, because `HTMLMediaElement.audioTracks` — the standard way to ask — is not
implemented in Chrome, which is precisely the browser without the decoder; see
`lib/playback/audibility`. Transcoding is the obvious fix and it is the same refusal
as above, one layer down: re-encoding somebody's stream is serving its bytes.

The applications remain the real players. That matches what the product is: a website
that manages an account and two applications that watch television.

The stream URL reaches client JavaScript and therefore the browser's network panel. It
is the user's own credential on the user's own machine, which is acceptable; it also
means an XSS on this site walks away with it, which is one more reason nothing about
the player is server-rendered and why the session cookie stays `httpOnly`.

If usage shows option A failing for the overwhelming majority, reopening it is a new
ADR with a bandwidth estimate in hand — not an amendment to this one.
