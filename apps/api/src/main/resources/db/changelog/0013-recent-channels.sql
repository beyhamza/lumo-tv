--liquibase formatted sql
--
-- Live channels the user watched recently (contract M5).

--changeset hamza:0013-01-create-recent-channel
--comment: the television's first rail, and the reason it is worth anything
-- A table of its own, NOT a third value on playback_progress.item_type. A
-- playback position means nothing on a continuous stream: folding live channels
-- into that table would make position_ms a required column with no value to put
-- in it. Two concepts sharing storage because they render in the same rail is
-- the shortcut that bills six months later.
--
-- user_id is denormalised from the channel's source for the same reason as on
-- `favorite`: every query in this codebase filters on user_id, and a rule that
-- needs a join to be satisfied is a rule that eventually gets skipped.
CREATE TABLE recent_channel (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    source_id  uuid        NOT NULL REFERENCES source(id) ON DELETE CASCADE,
    channel_id uuid        NOT NULL REFERENCES channel(id) ON DELETE CASCADE,
    watched_at timestamptz NOT NULL DEFAULT now()
);
--rollback DROP TABLE recent_channel;

--changeset hamza:0013-02-unique-recent-channel
--comment: PUT /me/recent-channels is an upsert on this pair
-- Watching the same channel again moves it to the top; it does not add a row.
CREATE UNIQUE INDEX recent_channel_user_channel_key
    ON recent_channel (user_id, channel_id);
--rollback DROP INDEX recent_channel_user_channel_key;

--changeset hamza:0013-03-index-recent-channel-order
--comment: the rail's own ordering, newest first
CREATE INDEX recent_channel_user_watched_at_idx
    ON recent_channel (user_id, watched_at DESC);
--rollback DROP INDEX recent_channel_user_watched_at_idx;
