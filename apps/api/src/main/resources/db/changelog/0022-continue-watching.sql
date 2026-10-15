--liquibase formatted sql
--
-- Continue watching (S12-01, ADR 0012): watched time as a union of intervals,
-- recency by observation time, and a hide that survives running sessions.

--changeset hamza:0022-01-progress-continue-columns
--comment: what the rail needs and a position alone cannot say
-- observed_at: when the device read the position; the latest wins (CW-30, CW-32).
-- watched_ms: legacy_watched_ms + the union of playback_watched_interval rows
--   (CW-17, CW-29), kept denormalised so the rail is one query.
-- legacy_watched_ms: the credit of rows saved before watched time existed — see
--   the backfill below. Not touched again.
-- hidden_at / restarted_after_hide: the hide (CW-10) and the "came back on a new
--   real start" rule that skips the 30 s wait (CW-18, CW-22).
-- last_session_started_at: the latest real start seen, from any device (CW-31).
ALTER TABLE playback_progress
    ADD COLUMN observed_at             timestamptz,
    ADD COLUMN watched_ms              bigint      NOT NULL DEFAULT 0,
    ADD COLUMN legacy_watched_ms       bigint      NOT NULL DEFAULT 0,
    ADD COLUMN hidden_at               timestamptz,
    ADD COLUMN restarted_after_hide    boolean     NOT NULL DEFAULT false,
    ADD COLUMN last_session_started_at timestamptz;
--rollback ALTER TABLE playback_progress DROP COLUMN observed_at, DROP COLUMN watched_ms, DROP COLUMN legacy_watched_ms, DROP COLUMN hidden_at, DROP COLUMN restarted_after_hide, DROP COLUMN last_session_started_at;

--changeset hamza:0022-02-backfill-legacy-progress
--comment: existing rows keep their place in the rail after the upgrade
-- Before ADR 0012 the rail showed every saved position. Without a credit, every
-- existing card would vanish on deploy until 30 more seconds were watched — an
-- upgrade that loses the user's rail (R020-16 spirit). The position is the only
-- measure there is: it is credited once, as watched time.
UPDATE playback_progress
   SET legacy_watched_ms = position_ms,
       watched_ms        = position_ms,
       observed_at       = updated_at;
--rollback UPDATE playback_progress SET legacy_watched_ms = 0, watched_ms = 0, observed_at = NULL;

--changeset hamza:0022-03-create-watched-interval
--comment: the union of actual playback intervals, merged on write
CREATE TABLE playback_watched_interval (
    progress_id uuid        NOT NULL REFERENCES playback_progress(id) ON DELETE CASCADE,
    started_at  timestamptz NOT NULL,
    ended_at    timestamptz NOT NULL,
    CONSTRAINT playback_watched_interval_order_check CHECK (ended_at > started_at)
);
--rollback DROP TABLE playback_watched_interval;

--changeset hamza:0022-04-index-watched-interval
CREATE INDEX playback_watched_interval_progress_idx
    ON playback_watched_interval (progress_id, started_at);
--rollback DROP INDEX playback_watched_interval_progress_idx;
