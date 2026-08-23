# ADR 0004 — Two applicationIds, two Play listings

- **Date** : 2026-08-22
- **Status** : Accepted

## Context

One Gradle project produces both a phone application and a TV application. Google
supports shipping both form factors under a single listing, but IPTV players attract
manual review, and Android TV submissions are scrutinised more heavily than phone ones.

## Decision

Two application modules, two applicationIds, two Play listings:

- `tv.lumo.android` — phone and tablet
- `tv.lumo.androidtv` — Android TV

Everything below the UI layer (domain, network, cache, player) stays shared in `core/`
and `feature/`.

## Consequences

**Positive.** A rejection on one listing does not block the other. The phone app can
ship while the TV app is still in review. Each store listing, screenshot set and
description is tailored to its form factor.

**Negative.** Two listings to maintain, two review cycles, two sets of store assets.
Purchases do not carry across listings — which is precisely why entitlements live
server-side (ADR 0003).

## Revisit if

Google's form-factor guidance changes, or maintaining two listings proves more costly
than the isolation is worth.
