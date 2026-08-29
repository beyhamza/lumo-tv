# ADR 0010 — Series are an Xtream feature, and the absence is said out loud

- **Date** : 2026-08-29
- **Status** : Accepted

## Context

Sprint 6 adds series. For an Xtream source the question does not arise: the panel
has `get_series` and `get_series_info`, and the provider has already declared the
tree. For an M3U playlist there is nothing to read — and this time "nothing to
read" is a different problem from the one [`ADR 0009`](./0009-m3u-film-detection.md)
solved.

A playlist is a flat list of entries. What exists are **naming conventions**, and
there are many:

| Written as | Seen in |
|---|---|
| `Nom S01 E02` | the most common, and the only near-universal one |
| `Nom - 1x02` | frequent, especially on Spanish and Italian panels |
| `Nom saison 1 épisode 2` | French panels, spelled out |
| `Nom 1ª Temporada Ep. 2` | Portuguese, with an ordinal indicator |
| `Nom [S1][E2]`, `Nom.s01e02.1080p` | scene-style, with the resolution attached |

`ADR 0009` accepted a heuristic for films and wrote down how it fails. The obvious
move is to do the same here. **It is the wrong move, and the reason is not that
this heuristic would be less accurate.**

### The difference that decides this

`ADR 0009` misfiles a **row**. An entry lands in the films grid instead of the
channel list; it plays, search finds it, and the user can see with their own eyes
that it is in the wrong place.

A series parser does not misfile a row. It **fabricates a structure**:

- a separator not recognised on half a series' entries produces **two series with
  the same name**, one of four episodes and one of sixteen;
- a title that happens to contain `1x02` — a channel called `Sport 1x02 Replay` —
  becomes an episode of a series called `Sport`;
- an episode whose title omits the token becomes a lone film, **outside the series
  it belongs to**, and nothing on screen says so;
- `Nom S01 E02` and `Nom - S01E02` from the same panel become two series.

And here is the part that settles it: **there is no ground truth to test against.**
`ADR 0009`'s classifier can be tested — a URL goes in, a `ContentType` comes out,
and its test is a table of the cases where it is wrong. A series parser can only be
tested against *what the parser thinks the title means*. Nothing in the playlist,
in the API, or anywhere else says how many seasons that series really has. A test
would assert the parser against itself.

**A badly sorted catalogue is corrected by eye. An invented tree is believed.**

## Decision

**Series are an Xtream feature in v1.** Four rulings.

### 1. An M3U entry that looks like an episode is classified by `ADR 0009`, and by nothing else

It is a film if its URL says file, a channel otherwise. Its name is shown
**verbatim** — `Nom S01 E02`, token included. Nothing is stripped, nothing is
grouped, no season heading is invented.

Stripping the token would be the same mistake in miniature: it would mean this
layer deciding that `S01 E02` is metadata rather than part of a title, which is
exactly the reasoning already refused for `Channel.quality` in
[`api-gaps.md`](../design/api-gaps.md) M4.

### 2. Nothing reads a human word, in any language

No keyword list, no per-language pattern set. The rule for M3U series is that there
is no rule — which is the one form that cannot be one language behind.

### 3. The absence is explained where a source is described, and **never as an empty tab**

This is the ruling that took the most argument, because it collides with a rule
already in force. `S5-08` decided that a source with no films shows **no films
tab**: an empty promise is worse than an absence. Applied here, that would mean an
M3U user never sees a Series tab and is never told why — which is the risk
[`sprint-06.md`](../backlog/sprint-06.md) named against this option.

Both are right, and they are about different places:

- **A tab is a promise.** It says "there is something here". It stays absent.
- **A source's own page describes what that source offers.** It already carries the
  channel and category counts. **That is where an absence belongs**, next to the
  things that are present.

So: no Series tab for a source that has none, and one sentence on the source page.
It names the reason and does not apologise.

> **FR** — « Cette source est une playlist M3U. Le format ne déclare ni saison ni
> épisode, et Lumo n'invente pas d'arbre à partir des titres. Les séries sont
> disponibles sur les sources Xtream. »
>
> **EN** — "This source is an M3U playlist. The format declares neither seasons nor
> episodes, and Lumo does not invent a tree from titles. Series are available on
> Xtream sources."

**An Xtream source that simply has no series gets a different sentence**, because
it is a different fact: the format could carry them and this provider offers none.
Collapsing the two would tell an Xtream user their panel cannot do something it
can.

> **FR** — « Ce fournisseur ne propose pas de séries. »
>
> **EN** — "This provider offers no series."

> **Superseded in part, by use, on 2026-08-29.**
>
> The reasoning above is about a *tab*, and it holds. What it got wrong is the
> conclusion drawn from it on the web: the films tab was hidden by the same
> argument, and the owner of a panel carrying **a hundred and forty thousand
> films** could not find them, concluded the feature did not exist, and reported
> it missing.
>
> **An absence is indistinguishable from a bug** — which is the failure this
> ruling was written to prevent, arriving through the door it left open. The web
> now shows all three catalogues always, and an empty one says so **in its own
> list**, with the sentences below. That is the one thing an absence can never
> do: explain itself.
>
> The half that survives: a *promise* is still bad. A tab that opens onto a
> sentence explaining that this source carries none is not a promise, it is a
> reply — and the sentences of this ruling are exactly what makes the difference.
>
> The phone and the television still hide theirs, and that is now an
> **inconsistency rather than a decision**. It is written down here rather than
> left to be found: whoever touches `CatalogueSections` next should make the two
> Android surfaces do what the web does.

### 4. What would reopen this, and what it would cost

Written now so the question is not reopened by whoever finds this file next.

**The shape**: a per-source setting the user turns on themselves — *"this group
contains episodes"* — accepting **one** naming convention, with the resulting tree
shown as provisional and correctable. It is the user asserting the structure, not
us guessing it, which is the only version of this that has a ground truth: theirs.

**The price is not the parser.** Reconstructing a tree moves rows from `vod_item`
to `episode`, and a saved position points at a `VodItem.id`
([`S5-11`](../backlog/sprint-05.md)). **Every progress row on a converted film is
orphaned** — the film that was resumable at 20:14 stops existing under that
identifier. That is a data migration with a user-visible loss, and it is the real
cost of changing our mind later. It is cheap today and it will not stay cheap.

Not this sprint.

## Rules

1. **Xtream never passes through any of this.** `get_series` at synchronisation for
   the flat list, `get_series_info` when somebody opens a series. The provider is
   authoritative, exactly as it is for films.
2. **No code path exists for M3U series.** Not a disabled one, not a flag defaulted
   to off. A dormant parser is a parser somebody enables to "see what it does".
3. **The two sentences of ruling 3 live in the message catalogues**, one key each,
   FR and EN, like every other string (`AGENTS.md` §4). They are not built by
   concatenating a source kind with a generic phrase.
4. **The Series tab follows the same mechanism as the films tab** —
   `CatalogueSections`, one request, false until something says otherwise. There is
   no second way of deciding whether a section exists.

## Consequences

**An M3U user never gets series.** That is the cost, stated rather than softened.
They get one sentence saying why, on the page where they would look.

**An entry named `Nom S01 E02` shows up in the films grid under that exact name.**
It is ugly and it is true. The alternative — a cleaned-up title — is a title we
made up.

**Nothing to maintain per language.** No convention list grows when a Portuguese
panel arrives.

**The series tree is never wrong**, because it is never ours. Whatever
`get_series_info` says is what is shown, including when the provider's own tree is
odd — which happens, and which is the provider's to fix.

**Changing our mind later costs a migration and loses progress**, per ruling 4. The
cheap moment to reverse this decision is now; there is not a second one.

## Alternatives considered

**Parse titles with a per-language convention set.** Rejected as the whole point of
this ADR: no ground truth, so no test that means anything, and the failures
fabricate structure rather than misplace rows.

**Parse only `SxxExx`, the near-universal one.** The tempting middle. Rejected
because **the parsing is not what fails — the grouping is.** Two entries of the same
series written with different punctuation become two series; an episode missing the
token leaves its own series silently. A rule that is right about each entry and
wrong about the set is worse than no rule, because the set is what the screen shows.

**Show M3U series as a flat list under a series heading.** A tree of depth one:
every "series-looking" group becomes a series, its entries become episodes with no
seasons. Still a fabrication, and one that looks more finished than it is — which
makes it harder to disbelieve, not easier.

**Ask the user to confirm the tree we guessed.** This is ruling 4's shape, and it
is the version that could work. It is deferred rather than rejected: it needs a
setting, an editing surface, and the migration cost above, and none of that fits a
sprint whose subject is the Xtream tree.

**Say nothing at all.** No tab, no sentence, silence. Rejected: an absence with no
explanation is indistinguishable from a bug, and the user's next move is a support
request about a feature that works.
