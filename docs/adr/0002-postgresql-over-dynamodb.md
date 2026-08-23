# ADR 0002 — PostgreSQL as primary datastore

- **Date** : 2026-08-22
- **Status** : Accepted

## Context

The team has prior DynamoDB experience, so it was a natural candidate. Two access
patterns dominate this product:

1. EPG lookups by time range across a set of channels — "what is on these 40 channels
   between 20:00 and 23:00".
2. VOD and channel catalogue search by name, with typo tolerance.

## Decision

PostgreSQL 16, with Liquibase migrations (see ADR 0006).

## Rationale

Both dominant patterns are range and text queries. In DynamoDB they require composite
sort keys plus secondary indexes, or an external search service — significant modelling
cost for queries Postgres answers with a B-tree and a `pg_trgm` index. Ingestion also
benefits from transactional bulk upserts when replacing a full catalogue.

Expected scale (tens of thousands of users, a few million EPG rows with automatic
retention) is comfortably within a single managed instance.

## Consequences

Vertical scaling has a ceiling; if EPG volume becomes the bottleneck, partition
`epg_programme` by day before considering another store. Requires connection pool
tuning that DynamoDB would not.

## Revisit if

EPG write throughput saturates the instance, or multi-region latency becomes a
requirement.
