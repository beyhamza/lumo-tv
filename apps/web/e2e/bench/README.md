# The source test bench

The container a qualification run points a source at, so that cases which need a
provider to misbehave can be played on demand and the same way twice. It serves
playlists, films, an Xtream panel and — since S9-07-03 — an XMLTV guide. It is
off unless the `bench` profile is named:

```sh
docker compose --profile bench --env-file apps/api/.env up -d
```

Paths and their fixtures live in `nginx.conf`; what has to be generated rather
than committed is built by `entrypoint.sh` at every start. The generated guide
is `xmltv.awk`'s.

## The guide: one file per scenario

The guide is generated at every `up`, around an anchor, so no committed file can
expire:

```sh
BENCH_EPG_ANCHOR=2026-09-26T20:00:00Z \
  docker compose --profile bench --env-file apps/api/.env up -d
```

`BENCH_EPG_ANCHOR` takes RFC 3339 or epoch seconds and defaults to the
container's start. Set it to the same instant as the web's `LUMO_NOW` for a
controlled-clock session. A source's `epg_url` is `http://bench/guide.xml` seen
from the API; the browser's direct control reads `http://localhost:18081/...`.

| File | Scenario | What it holds |
|---|---|---|
| `guide.xml` | Game A — the canonical grid | `bench.1` A1 `T→T+45`; `bench.2` B1 `T+15→T+30`; `bench.3` C1 `T→T+60`; `bench.4` a gap at `T+25` (D1 only `T+2h→T+3h`); `bench.5` has no `tvg_id`, so no guide |
| `guide-transition.xml` | Game B — a programme ending, another becoming current | `bench.1` E1 `T−30→T+2`, E2 `T+3→T+33` |
| `guide-partial.xml` | GD-11 — a gap, not an error | Game A without `bench.3` |
| `guide-empty.xml` | GD-11 — a slot with nothing | `<tv></tv>`, no programme |
| `guide-broken.xml` | GD-11 — a failed import | A valid opening, then an element never closed; `epg_attempt_status=FAILED` |
| `guide-stale.xml` | GD-11 — an old guide | Byte-identical to `guide.xml`; staleness is the DB's `epg_last_success_at`, moved by SQL (recette §2.4) |
| `guide-big.xml` | Game D — volume | 100 channels, 30-minute blocks, `bench.001`…`bench.100`, D−1→D+3 |
| `guide-huge.xml` | Volume past 4 MiB | `guide-big` squeezed into three hours with 8 192-character descriptions |

**Why Games A and B are two files.** Both put a programme on `bench.1` at `T`,
and an XMLTV feed cannot hold two overlapping programmes on one channel — a
single `guide.xml` that tried would be either wrong or unplayable. A1
(`T→T+45`) and E1/E2 (`T−30→T+2`, `T+3→T+33`) are therefore served apart, and a
session changes the source's `epg_url` between the two. `guide.xml` is the one
the fixtures name by default; `guide-transition.xml` is out of the I-2 list on
purpose.

**Why `stale` is a copy.** Freshness is not a property of the bytes; it is the
time of the last successful import, which lives in the database. The recette
reaches it with an `UPDATE`, which is the only way to move it without waiting.

`playlist-100.m3u` is aligned with `guide-big.xml`: 100 channels `bench.001` to
`bench.100`, so the volume proof (`S9-04-05`, `S9-05-02`) compares 3, 50 and 100
channels over the same window.

## Instrumentation for the qualification run

Two measurements cannot be taken from the browser, and both live on the API
side.

### I-4 — how many guide reads the web made

The web renders its guide on the Next.js server, so `page.on("request")` never
sees the EPG call. It is counted from the API's request log instead:

```sh
SPRING_PROFILES_ACTIVE=dev,epg-logging \
  docker compose --env-file apps/api/.env up -d --no-deps lumo-api
# clear the log, load the Guide in the browser, then:
apps/web/e2e/bench/count-epg.sh
```

The `epg-logging` profile turns on
`org.springframework.web.servlet.DispatcherServlet` — **not** `FrameworkServlet`,
and the name is case-sensitive, which is why it is written as a bracketed YAML
key (`application-epg-logging.yml`) rather than a `LOGGING_LEVEL_…` variable
(relaxed binding lowercases it, and the logger no longer exists). The expected
count is small and independent of the number of channels drawn: one in the
normal case, `1 + 2 + 4` when the window is refused as too large and split.
Repeat at 3, 50 and 100 channels and copy the three counts into the report.

### I-5 — a guide read that fails on demand

GD-10's second half needs a grid already on screen and a guide read that then
fails. Arm the fault on the API and only the two guide reads are refused:

```sh
LUMO_EPG_FAULT=503 docker compose --env-file apps/api/.env up -d --no-deps lumo-api
```

Unset means off, which is what ships, and the value must be `0` or a 5xx — a
typo stops the container rather than answering an unexpected body. The catalogue
and the source list keep answering, which is the point: "the guide is refused
while the rest of the screen is fine" is the case to play. It is implemented by
`tv.lumo.api.epg.EpgFaultInjection`, called first in both guide handlers.
