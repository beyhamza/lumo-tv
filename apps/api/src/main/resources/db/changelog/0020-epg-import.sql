--liquibase formatted sql
--
-- What this server knows about its own import of a source's guide (US-16,
-- lot C1, D3). Five additive columns on `source`, read back as the contract's
-- `EpgImportStatus`, written only by the ingestion worker and reset by a PATCH
-- that changes what gets fetched.
--
-- **`last_synced_at` is NOT copied into any of them, on purpose.** That date is
-- the catalogue's, and the bench proved it says nothing about the guide: a
-- guide that broke after several batches were written still ends with the
-- source READY and `last_synced_at` set (docs/releases/0.2.0/s9-00-epg-bench.md).
-- Backfilling it here would mint a "last guide import" date for guides that
-- never finished importing, which is the one lie the whole lot exists to stop.
-- Every source that predates this file is therefore UNKNOWN with no date, and
-- says so until its next import writes something true.

--changeset hamza:0020-01-source-epg-last-success-at
--comment When the last guide import that finished wrote its last batch.
--
-- The date the interface shows as "last guide import" (D3). Written once per
-- successful attempt, after `flushNow()`, never before: a date stamped when the
-- fetch started would be the date of an attempt, not of a guide. It survives a
-- later failure untouched, because the guide it dates is still the one stored.
--
-- It dates the import, never the content: nothing in an XMLTV file says when
-- the provider published it, and this column does not pretend otherwise.
ALTER TABLE source ADD COLUMN epg_last_success_at timestamptz;

--rollback ALTER TABLE source DROP COLUMN epg_last_success_at;

--changeset hamza:0020-02-source-epg-attempt-started-at
--comment When the latest guide import attempt began.
--
-- The guide's own attempt, distinct from the synchronisation that contains it:
-- `updated_at` moves at every step of the catalogue and cannot date this one.
-- Written before the first batch, so a RUNNING attempt is dated from the moment
-- it could have started changing rows.
ALTER TABLE source ADD COLUMN epg_attempt_started_at timestamptz;

--rollback ALTER TABLE source DROP COLUMN epg_attempt_started_at;

--changeset hamza:0020-03-source-epg-attempt-finished-at
--comment When that attempt ended, whatever its outcome. NULL while it runs.
ALTER TABLE source ADD COLUMN epg_attempt_finished_at timestamptz;

--rollback ALTER TABLE source DROP COLUMN epg_attempt_finished_at;

--changeset hamza:0020-04-source-epg-attempt-status
--comment The outcome of the latest guide import attempt.
--
-- NOT NULL with a default rather than nullable, because "no value" already has
-- a name in the contract — UNKNOWN — and a nullable column would have given it
-- two spellings. Every existing row takes the default: unknown, not succeeded,
-- not failed.
ALTER TABLE source ADD COLUMN epg_attempt_status text NOT NULL DEFAULT 'UNKNOWN';

--rollback ALTER TABLE source DROP COLUMN epg_attempt_status;

--changeset hamza:0020-05-check-source-epg-attempt-status
--comment The five values of the contract's EpgAttemptStatus, and no other.
--
-- Same reason as `source_status_check`: clients switch on this column's value,
-- so a typo in a writer must fail here rather than reach a screen as a status
-- nobody translates.
ALTER TABLE source
    ADD CONSTRAINT source_epg_attempt_status_check CHECK (
        epg_attempt_status IN ('UNKNOWN', 'RUNNING', 'SUCCEEDED', 'FAILED', 'INTERRUPTED')
    );

--rollback ALTER TABLE source DROP CONSTRAINT source_epg_attempt_status_check;

--changeset hamza:0020-06-source-epg-attempt-id
--comment The identity of the running attempt: what lets a stale one be refused.
--
-- Minted by the worker before its first batch and required, as a WHERE clause,
-- by every later write of the attempt's outcome. A PATCH that changes the
-- guide's configuration clears it, so an attempt started under the previous
-- URL finds no row to publish its success on and its result is dropped — which
-- is the only honest thing to do with a success measured against a
-- configuration that no longer exists (C1-10). Not a foreign key to anything:
-- there is no attempt table and there will not be one; the id exists to be
-- compared, not to be looked up.
ALTER TABLE source ADD COLUMN epg_attempt_id uuid;

--rollback ALTER TABLE source DROP COLUMN epg_attempt_id;
