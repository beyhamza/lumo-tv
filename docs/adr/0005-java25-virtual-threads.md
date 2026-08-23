# ADR 0005 — Java 25 and virtual threads, blocking style

- **Date** : 2026-08-23
- **Status** : Accepted

## Context

The ingestion layer is overwhelmingly I/O-bound: it fetches M3U playlists, gzipped
XMLTV guides and Xtream `player_api.php` responses from arbitrary third-party servers,
many of which are slow. A single sync can hold a thread for tens of seconds doing
nothing but waiting on a socket.

Classic options were a large platform-thread pool (memory-expensive, still capped) or
a reactive stack (WebFlux, `Mono`/`Flux`) which buys throughput at a steep cost in
readability and debuggability.

Java 25 changes the calculus. JEP 491, shipped in JDK 24, removed carrier-thread
pinning on `synchronized` blocks — historically the single biggest reason virtual
threads underdelivered on real codebases. Scoped values are finalised in JDK 25.

## Decision

Java 25 (LTS), Spring Boot 4.1.x, `spring.threads.virtual.enabled=true`.

**Blocking programming style throughout.** Plain sequential code, blocking JDBC,
blocking HTTP clients. No WebFlux, no `Mono`/`Flux`, no chained `CompletableFuture` in
business logic. Stack traces stay readable and debuggers stay useful.

No preview features are enabled. Structured concurrency remains preview in JDK 25;
until it is final, concurrent fan-out uses
`Executors.newVirtualThreadPerTaskExecutor()` in try-with-resources.

## Consequences — the traps this project will actually hit

**1. Backpressure disappears, and that is the dangerous part.**
A bounded thread pool used to be implicit backpressure. Virtual threads remove that
ceiling. Ten thousand concurrent syncs will happily open ten thousand outbound
connections to a user's IPTV provider. That is not a performance problem, it is a
product problem: we would hammer a third-party server and get our user's account
throttled or banned.

Backpressure must therefore be **explicit and deliberate**: a `Semaphore` per
destination host on every outbound call in `ingest/`, plus a global cap on concurrent
syncs. This is mandatory, not an optimisation.

**2. The connection pool is still the real limit.**
Virtual threads do not create database capacity. Ten thousand virtual threads against
a HikariCP pool of twenty means queueing, and pool-acquisition timeouts that surface as
confusing failures. Pool size stays sized to the database, and acquisition timeouts are
set and monitored.

**3. `ThreadLocal` becomes a memory leak.**
With millions of short-lived threads, per-thread caching of expensive objects is a leak
by construction. Request-scoped context uses `ScopedValue`. Third-party libraries
relying on `ThreadLocal` need auditing before adoption.

**4. Never pool a virtual thread.**
Virtual threads are cheap and disposable. `newFixedThreadPool` with a virtual thread
factory defeats the entire mechanism.

**5. `synchronized` around blocking I/O.**
No longer pins the carrier on JDK 24+, but still serialises callers. Prefer
`ReentrantLock`, and question why a lock surrounds I/O at all.

## Revisit if

Structured concurrency reaches final status — the ingestion fan-out is its textbook
use case and should be migrated then.
