# ADR 0001 — Contract-first via OpenAPI

- **Date** : 2026-08-22
- **Status** : Accepted

## Context

Three client surfaces (Android mobile, Android TV, web) consume one backend, and much
of the work is delegated to parallel AI agents. The main failure mode in that setup is
drift: an agent invents an endpoint or silently changes a field name, and the mismatch
only surfaces at runtime, in another application, days later.

## Decision

`packages/contracts/openapi.yaml` is hand-written and is the single source of truth.

- Backend: `openapi-generator`, `spring` generator, `interfaceOnly=true`. Controllers
  implement generated interfaces, so contract drift is a **compile error**.
- Android: `openapi-generator`, `kotlin` generator, `jvm-retrofit2` library.
- Web: `openapi-typescript` for types, `openapi-fetch` for the client.

Generation runs in CI. A build fails if generated output differs from what is committed.

## Consequences

**Positive.** Client agents can work against a contract before the backend exists.
Breaking changes are caught at build time, on every surface at once. The contract
doubles as API documentation.

**Negative.** Editing the contract is a slightly heavier ritual than editing a DTO.
Generated code is verbose and occasionally needs manual wrappers. Some Spring
annotations are awkward to express in OpenAPI.

**Rejected alternative.** Code-first with springdoc, exporting the spec from a running
server. Simpler day-to-day, but clients then depend on the backend existing and running,
which defeats parallel work — the primary constraint here.
