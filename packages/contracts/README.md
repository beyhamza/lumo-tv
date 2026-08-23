# `@lumo/contracts` — the API contract

`openapi.yaml` in this directory is **hand-written** and is the single source of
truth for the Lumo TV API (AGENTS.md §3, ADR 0001).

Nothing here is application code. This package holds one document and the
pipeline that turns it into three clients.

## The rule

> You never change a request or response type in client or server code.
> You change `openapi.yaml`, you regenerate, then you adapt.

That inversion is what lets agents work on `apps/web`, `apps/android` and
`apps/api` in parallel without colliding. It is also why a contract change must
be announced explicitly in its pull request description: it lands on three
surfaces at once.

If the contract does not cover what you need, **stop and ask**. Do not invent an
endpoint.

## Layout

```
packages/contracts/
├─ openapi.yaml            ← THE source of truth. Hand-written.
├─ redocly.yaml            ← lint rules
├─ openapitools.json       ← pinned openapi-generator version
├─ config/
│  ├─ spring.yaml          ← Spring server generator options
│  ├─ kotlin.yaml          ← Kotlin/Retrofit2 client options
│  └─ openapi-generator-ignore
├─ scripts/                ← executable pipeline
└─ generated/              ← COMMITTED output. Never hand-edited.
   ├─ spring/              → apps/api implements these interfaces
   ├─ kotlin/              → apps/android core/network
   └─ typescript/api.d.ts  → apps/web, through openapi-fetch
```

## Commands

```sh
npm ci --prefix packages/contracts          # once
npm --prefix packages/contracts run lint    # validate the contract alone
npm --prefix packages/contracts run generate # regenerate all three clients
npm --prefix packages/contracts run check    # what CI runs
```

`generate` lints first, then rewrites each output directory **from empty**.
That matters: `openapi-generator` never deletes a file it no longer emits, so
removing an operation from the contract would otherwise leave an orphan class
behind — still compiling, still wrong.

Requires a JDK (the generator is a Java tool; the npm package only wraps the
jar) and Node.

## Why generated code is committed

`check` regenerates into a throwaway directory and diffs it against
`generated/`. A difference fails the build.

The alternative — generating at build time and committing nothing — hides
contract changes from code review. Committing the output means a pull request
that renames a field shows, in its own diff, every client property it breaks.

**Never hand-edit anything under `generated/`.** The check will catch it, and
the fix is always to regenerate.

## Two invariants this contract enforces

1. **The Xtream password appears in no response schema, anywhere.** It is
   write-only on `CreateSourceRequest` and `UpdateSourceRequest` and nowhere
   else. `Source` has no password property and never will.

2. **`stream_url` never appears in a listing.** It is emitted only by
   `GET /channels/{id}/playback`, one channel at a time, after an ownership
   check. A list of a thousand channels does not carry a thousand stream URLs.

Both are verifiable against the generated code, which is part of why it is
committed:

```sh
grep -rl "streamUrl" packages/contracts/generated/spring/   # PlaybackInfo only
```

Every secret-bearing property is additionally typed `format: password`. That
carries no UI meaning here — it makes the generators mask the property in
`toString()`, so a token, a password or a stream URL cannot reach a log line by
accident (AGENTS.md §5).

## Generator choices worth knowing

**Spring — `interfaceOnly` *and* `skipDefaultInterface`.** The generated
interfaces have no default methods. A controller that does not match the
contract does not compile, which is exactly what ADR 0001 asks for. The
consequence to plan around: every operation of a tag must be implemented before
that tag's interface compiles, so a partially built sprint needs explicit stubs
rather than silently inheriting a `501`.

**Kotlin — `jvm-retrofit2` with coroutines and Moshi.** Every operation is a
`suspend fun`. Moshi emits `@Json(name = "user_code")`, so the contract's
snake_case survives into idiomatic camelCase Kotlin properties.

**TypeScript — types only.** `openapi-typescript` emits no runtime; `apps/web`
consumes the types through `openapi-fetch`. There is no generated JavaScript to
keep in sync.

**Payload casing is snake_case**, matching `docs/domain-model.md` §2 field names
exactly — no synonyms, no case variants. Query parameters keep the spelling of
`docs/domain-model.md` §3 (`contentType`, `categoryId`, `q`, `page`, `size`,
`from`, `to`). Both generators map this to idiomatic properties in their own
language, so the snake_case is invisible in application code.
