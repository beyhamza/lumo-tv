--liquibase formatted sql
--
-- The two phases the CHECK constraint never learnt about.
--
-- `0010-source-sync-diagnostics.sql` wrote the list of valid `sync_step` values
-- into a CHECK constraint. `SyncStep` then grew `PARSING_VOD` in sprint 5 and
-- `PARSING_SERIES` in sprint 6, in the contract and in the enumeration — and not
-- here.
--
-- The consequence was not a rejected step. `markSyncStep(PARSING_VOD)` threw a
-- constraint violation, which is a `DataIntegrityViolationException` and not an
-- `IngestionException`, so it went straight past the handler that exists to keep
-- a film catalogue's failure from failing its source. **Every Xtream source
-- ingested since sprint 5 ended in ERROR, and lost its channels with its films.**
-- The reported code was `SOURCE_UNREACHABLE`, which named the user's provider for
-- a fault entirely on this side.
--
-- Two lessons, and the second is the one worth keeping: an enumeration duplicated
-- into a CHECK constraint is a second place to remember, and nothing in the build
-- connects them. The reason it survived two sprints is that nothing in the test
-- suite exercises `IngestionService` — a gap named in `sprint-05-recette.md` §11
-- and in `dette.md`, and this is what it cost.

--changeset hamza:0017-01-widen-sync-step-check
--comment: dropped and recreated rather than added beside: two CHECK constraints
--comment: on one column both have to pass, so a second permissive one changes
--comment: nothing while the first still refuses.
ALTER TABLE source DROP CONSTRAINT source_sync_step_check;

ALTER TABLE source
    ADD CONSTRAINT source_sync_step_check CHECK (
        sync_step IS NULL
        OR (status = 'SYNCING'
            AND sync_step IN ('CONNECTING', 'AUTHENTICATED', 'PARSING_CHANNELS',
                              'PARSING_VOD', 'PARSING_SERIES', 'FETCHING_EPG'))
    );
--rollback ALTER TABLE source DROP CONSTRAINT source_sync_step_check;
--rollback ALTER TABLE source ADD CONSTRAINT source_sync_step_check CHECK (sync_step IS NULL OR (status = 'SYNCING' AND sync_step IN ('CONNECTING', 'AUTHENTICATED', 'PARSING_CHANNELS', 'FETCHING_EPG')));

--changeset hamza:0017-02-clear-errors-from-the-constraint
--comment: the sources this broke are in ERROR with SOURCE_UNREACHABLE, which is a
--comment: lie about their provider. Put back to PENDING so the next sweep picks
--comment: them up, rather than leaving somebody to work out that the error is ours.
--comment:
--comment: Scoped to XTREAM: an M3U source never reaches PARSING_VOD as a step —
--comment: a playlist is read in one pass — so an M3U source in this state failed
--comment: for a real reason and keeps it.
UPDATE source
   SET status = 'PENDING', error_code = NULL, last_error_at = NULL, sync_step = NULL
 WHERE kind = 'XTREAM'
   AND status = 'ERROR'
   AND error_code = 'SOURCE_UNREACHABLE';
--rollback SELECT 1;

--changeset hamza:0017-03-make-the-recovered-sources-reclaimable
--comment: 0017-02 put them in PENDING, and PENDING is a dead end: the auto-sync
--comment: sweep selects `status = 'READY'` only, so nothing would ever pick them
--comment: up again and the owner would have to re-synchronise by hand — which is
--comment: precisely what this migration existed to spare them.
--comment:
--comment: READY is also the truthful state rather than a convenient one: their
--comment: channels and categories are in the table, ingested by the sync that ran
--comment: before the film step threw. What is stale is the catalogue, and that is
--comment: exactly what `last_synced_at` is for — backdated past the auto-sync
--comment: window so the next sweep reclaims them.
--comment:
--comment: A new changeset rather than an edit to 0017-02: that one has already run
--comment: on a real database, and Liquibase computes a checksum over what it
--comment: executed (ADR 0006). Rewriting applied history is how a schema and its
--comment: log stop agreeing.
UPDATE source
   SET status = 'READY',
       last_synced_at = now() - interval '13 hours'
 WHERE kind = 'XTREAM'
   AND status = 'PENDING'
   AND sync_step IS NULL
   AND error_code IS NULL;
--rollback SELECT 1;
