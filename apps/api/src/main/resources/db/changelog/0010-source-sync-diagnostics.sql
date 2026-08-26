--liquibase formatted sql
--
-- What a source tells the user while it works, and after it fails (contract M1, G5).

--changeset hamza:0010-01-add-source-sync-step
--comment: how far the running ingestion has got; NULL unless it is running
-- Kept on the row rather than in the worker's memory, which is where
-- docs/domain-model.md first put it. A client polls GET /sources/{id} and there
-- is no guarantee it reaches the instance running the ingestion, so an in-memory
-- step would be visible to some polls and not others -- a checklist that goes
-- backwards, which is worse than no checklist at all.
ALTER TABLE source
    ADD COLUMN sync_step text;
--rollback ALTER TABLE source DROP COLUMN sync_step;

--changeset hamza:0010-02-check-source-sync-step
--comment: the contract's "non-null only while SYNCING", enforced by the database
-- Both halves matter. The value list keeps a typo out of a column clients switch
-- on; the status clause keeps a stale step from surviving the ingestion that set
-- it, which would show "fetching the guide" next to a source that failed an hour
-- ago. Every writer of `status` must therefore clear `sync_step` in the same
-- statement, and this constraint is what makes forgetting it a failure here
-- rather than a wrong screen there.
ALTER TABLE source
    ADD CONSTRAINT source_sync_step_check CHECK (
        sync_step IS NULL
        OR (status = 'SYNCING'
            AND sync_step IN ('CONNECTING', 'AUTHENTICATED', 'PARSING_CHANNELS', 'FETCHING_EPG'))
    );
--rollback ALTER TABLE source DROP CONSTRAINT source_sync_step_check;

--changeset hamza:0010-03-add-source-last-error-at
--comment: when the ingestion that set error_code failed (contract G5)
-- Separate from last_synced_at because the two answer different questions and one
-- timestamp cannot answer both: "1 248 chaines - verifie il y a 2 h" on one line,
-- "identifiants refuses depuis hier" on the next. The age is what makes the
-- second actionable.
ALTER TABLE source
    ADD COLUMN last_error_at timestamptz;
--rollback ALTER TABLE source DROP COLUMN last_error_at;
