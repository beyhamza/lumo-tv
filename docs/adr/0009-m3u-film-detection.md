# ADR 0009 — A film is recognised in an M3U by its URL, and by nothing else

- **Date** : 2026-08-28
- **Status** : Accepted

## Context

Sprint 5 adds films. For an Xtream source the question does not arise: the panel has
`get_vod_categories` and `get_vod_streams` beside `get_live_streams`, and the
provider has already answered it. For an M3U playlist there is no answer to read.

A playlist is a flat list of entries. Nothing in the format declares a type — there
is no `content-type` attribute, no section header, no convention any generator
agrees on. What exists are **hints**, and they are of two very different kinds:

| Hint | Kind | What it is worth |
|---|---|---|
| The URL path ends in `.mkv`, `.mp4`, `.avi`, `.m4v` | structural | A live stream is not served as a file. Strong |
| The URL path contains `/movie/` | structural | Xtream's own path segment, in an exported playlist. Strong |
| `group-title` contains `VOD`, `MOVIE`, `FILM` | free text | Common, and wrong the moment a channel is called `Ciné+` |
| No `tvg-id` | absence | Far too weak alone: most playlists carry none on most entries |

The first two describe **what the thing is**. The last two describe **what somebody
called it**, and people call live channels `CINE+`, `Film4`, `VOD Sports News`.

This decision has to be taken before a line of `M3uStreamParser` changes, because a
heuristic written inline is a heuristic nobody can argue with afterwards — and this
one *will* be wrong sometimes. What matters is that it is wrong in a predictable
direction, and that the direction was chosen.

## Decision

**Three rulings, and a fourth that follows from them.**

### 1. Only the URL classifies. One structural hint is enough; no number of textual ones is.

An entry is `VOD` if, and only if, its URL matches one of the two structural hints.
`group-title` and a missing `tvg-id` **do not classify at all** — not alone, not in
combination, not as a tie-breaker.

This is deliberately not "two hints out of four". Counting hints of different quality
is how a rule stops being explicable: `group-title="VOD Sport"` plus a missing
`tvg-id` would file a live sports channel under films, and nobody reading the code
six months later could say whether that was intended.

The URL is also the only hint whose meaning does not depend on a language. A French
playlist writes `FILMS`, a Spanish one `PELÍCULAS`, and a list of keywords per
language is a list that is permanently one language behind.

### 2. Doubt falls towards `LIVE`

An entry with no structural hint is a channel. This is not symmetry — the two errors
cost very different things.

| Error | What the user sees |
|---|---|
| A film filed among the channels | It appears in the channel list, it plays, search finds it. Untidy |
| A channel filed among the films | A poster grid with no poster, a "resume at 20 min" that means nothing on a continuous stream, and a progress row written for something that has no position |

The second is not untidy, it is broken — and `playback_progress` would start
accumulating rows for live channels, which the contract says outright is a client
bug rather than a supported case.

### 3. The user cannot correct it yet, and the correction has a designated shape

Not in sprint 5. But the shape is decided now, so that nothing built in this sprint
gets in its way: **the override belongs on the category, not on the source.**

A source carries both kinds — that is the normal case for the playlists this rule
exists for. A per-source switch would therefore be wrong for every source it was
offered on. A category, on the other hand, is exactly the `group-title` the user
already sees and already thinks in: *"this group is a film catalogue"* is a sentence
they can evaluate.

Cost, so it can be scheduled rather than rediscovered: one nullable field on
`Category` in the contract, one column, one `PATCH`, and a control on each of the
three surfaces. **Three points.** It is written in `sprint-05.md`'s out-of-scope
section, not here, because this ADR decides its shape and not its date.

### 4. An M3U film carries no `container_extension`

It follows from ruling 1 and it constrains the contract task that comes next.

An Xtream film needs `container_extension` because its playback URL is *built* —
`/movie/{user}/{pass}/{id}.{ext}` — and the extension is the only part the panel does
not give in the path. An M3U entry has no such problem: the playlist carries the
complete URL, extension included, which is precisely what ruling 1 reads.

So the field is populated for Xtream and null for M3U, and anything that treats it as
required will reject half the sources this ADR exists for.

## Rules

1. **Xtream never passes through this.** `get_vod_streams` and `get_live_streams` are
   authoritative, and a source that has them has already answered. Applying the
   heuristic there "for symmetry" would let a URL shape overrule a provider.
2. **The classifier is one function, in one place, testable without a network.** An
   entry in, a `ContentType` out. A rule spread across the parser is a rule that
   cannot be shown to be wrong.
3. **Its test is a table of the cases where it fails**, not a list of the ones where
   it works. `.mkv`; `group-title="VOD - ACTION"` served as `.m3u8`; a channel named
   `Ciné+ Premier`; an entry with neither extension nor `tvg-id`. The test says *how*
   the rule is wrong, because pretending it never is would be the lie.
4. **The counts are reported separately.** An ingestion says "842 channels,
   12 400 films", never a single total: a total hides the case where one of the two
   is zero because the rule went one way for everything.

## Consequences

**A live channel served as a progressive `.mp4` will be filed as a film.** It exists,
it is rare, and it is the residual risk this decision accepts rather than hides. The
user's way out is the category override of ruling 3, which is why that ruling has a
shape and a price today.

**A film in a playlist that names its group `Films` and serves `.m3u8` stays a
channel.** Ruling 2 chose this: it plays, it is searchable, it is merely in the wrong
list.

**No language list to maintain.** Nothing in the rule reads a human word, so nothing
has to be extended when a Portuguese playlist arrives.

**The rule can be tightened later without a migration.** Classification happens at
ingestion and the result is a `category.content_type`; a re-synchronisation
reclassifies everything. Favourites and progress point at identifiers that survive it
(`(source_id, external_id)`), so a better rule costs a re-sync and nothing more.

## Alternatives considered

**Count concordant hints, textual ones included.** Rejected in ruling 1: it files
live sports channels under films, and no reader can reconstruct why.

**Parse the entry name for a year, `S01E02`, or a resolution.** This is what existing
players do. It works often enough to be tempting and produces `Cinéma HD Premium`
displayed as `Cinéma Premium` with a false badge — the same failure ADR-level
reasoning already rejected for `Channel.quality` in `api-gaps.md` M4.

**Ask the server to probe the URL.** A `HEAD` per entry, on a playlist with fifteen
thousand of them, against the user's own provider. That is exactly the hammering the
rest of this system is built to avoid (`SOURCE_SYNC_RATE_LIMITED`), and it would make
an import take an hour.

**Do nothing: films are Xtream-only.** Tenable — and it is what
[`ADR 0010`](./0010-series-are-xtream-only.md) went on to decide for *series*, where
a wrong answer fabricates a tree rather than misfiling a row and **there is no ground
truth to test it against**. It is not tenable here: a film misfiled is a film in the
wrong list, visible to the person looking at it, and refusing the whole feature to M3U
users over that is a worse trade.
