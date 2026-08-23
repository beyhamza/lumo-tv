# ADR 0006 — Liquibase with formatted SQL changesets

- **Date** : 2026-08-23
- **Status** : Accepted
- **Completes** : ADR 0002, which names Liquibase and leaves the format to this ADR
- **Amended** : 2026-08-23, on the maintainer's instruction. The header read
  *Supersedes: the Flyway choice implied by ADR 0002*. There is no such choice to
  supersede: ADR 0002 has a single version, from the initial commit, and its
  Decision reads "PostgreSQL 16, with Liquibase migrations (see ADR 0006)" — a
  forward reference to this document, not a decision this one reverses. A reader
  who took the header at face value would have gone looking through the history
  for a Flyway period that never existed. Only that line changed; the decision,
  the rules and the consequences below are untouched.

## Context

Migrations must be readable as plain SQL. Reviewing a schema change should not mean
mentally compiling XML or YAML into DDL, and PostgreSQL-specific constructs this
project needs — `citext`, partial indexes, `pg_trgm` GIN indexes, `bytea` columns,
`CREATE EXTENSION` — are awkward or impossible to express in Liquibase's abstract
change types.

Flyway is the obvious SQL-first tool, and was the alternative weighed here — weighed,
never adopted. Liquibase is chosen instead for its changeset metadata: explicit
rollback blocks, preconditions, contexts and labels, checksum tracking with
`runOnChange`. These matter more than Flyway's simpler model once the schema is live.

## Decision

Liquibase, with **every changeset written in formatted SQL**.

One structural exception is unavoidable. Liquibase open source requires the *root*
changelog to be XML, YAML or JSON when it uses `include` or `includeAll`; SQL root
changelogs with those directives exist only in Liquibase Secure 4.28.0 and later.
We therefore keep exactly one YAML file, roughly ten lines:

```
src/main/resources/db/
├─ db.changelog-master.yaml        # the only non-SQL file — includeAll, nothing else
└─ changelog/
   ├─ 0001-extensions.sql
   ├─ 0002-users-and-auth.sql
   ├─ 0003-devices-and-tokens.sql
   ├─ 0004-sources.sql
   ├─ 0005-catalog.sql
   ├─ 0006-epg.sql
   ├─ 0007-userdata.sql
   └─ 0008-entitlements.sql
```

Changeset format:

```sql
--liquibase formatted sql

--changeset hamza:0004-01-create-source
--comment: user IPTV sources, credentials encrypted at rest
CREATE TABLE source (
    id              uuid PRIMARY KEY,
    user_id         uuid NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    ...
);
--rollback DROP TABLE source;
```

## Rules

- One logical change per changeset. Never edit an applied changeset: add a new one.
- Every changeset carries an explicit `--rollback`. Use `--rollback empty` when a
  rollback is genuinely a no-op, so the intent is recorded rather than forgotten.
- Filenames are zero-padded and ordered; `includeAll` sorts alphabetically, so naming
  *is* the execution order.
- No `changeSet` written in YAML or XML. If a change seems to require it, it does not.

## Consequences

Migrations are reviewable as SQL in a diff. PostgreSQL features are available without
workarounds. Database portability is abandoned — an acceptable trade given ADR 0002.
One YAML file remains, and that is a hard limit of the open-source edition, not a
preference.
