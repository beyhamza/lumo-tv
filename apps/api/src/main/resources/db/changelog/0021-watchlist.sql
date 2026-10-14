--liquibase formatted sql
--
-- The watchlist (S11-01, ADR 0011): films and series an account saved on
-- purpose, and the record of the intentions that changed it.

--changeset hamza:0021-01-create-watchlist-item
--comment: its own table, not a reuse of favorite or playback_progress (ADR 0011, ruling 1)
-- Identity is (source, type, the source's own identifier) — never a catalogue
-- row id and never a title. A film that disappears is deleted from vod_item and
-- comes back, if it does, with a new id: the item must outlive the first and
-- recognise the second (WL-01, WL-15). So no foreign key to the catalogue, and
-- availability is a join at read time rather than a stored flag that ingestion
-- would have to keep in step.
--
-- title and poster_url are the display snapshot taken when the item was added:
-- what the card shows once the catalogue has nothing left to show (ruling 3).
--
-- source_id cascades: deleting a source removes its items, they do not linger
-- as unavailable (WL-05, US-024). user_id is denormalised for the same reason as
-- on favorite: every query here filters on it without a join.
CREATE TABLE watchlist_item (
    id           uuid        PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    source_id    uuid        NOT NULL REFERENCES source(id) ON DELETE CASCADE,
    content_type text        NOT NULL CHECK (content_type IN ('VOD', 'SERIES')),
    external_id  text        NOT NULL,
    title        text        NOT NULL,
    poster_url   text,
    -- clock_timestamp(), not now(): the order is the order the server accepted
    -- the adds in, and now() is the start of a transaction that may commit
    -- after a later one.
    added_at     timestamptz NOT NULL DEFAULT clock_timestamp()
);
--rollback DROP TABLE watchlist_item;

--changeset hamza:0021-02-unique-watchlist-item
--comment: one item per content per account; a second add is a no-op on this key
CREATE UNIQUE INDEX watchlist_item_identity_key
    ON watchlist_item (user_id, source_id, content_type, external_id);
--rollback DROP INDEX watchlist_item_identity_key;

--changeset hamza:0021-03-index-watchlist-item-order
--comment: the list's own ordering, most recently added first
CREATE INDEX watchlist_item_user_added_at_idx
    ON watchlist_item (user_id, added_at DESC, id DESC);
--rollback DROP INDEX watchlist_item_user_added_at_idx;

--changeset hamza:0021-04-create-watchlist-intention
--comment: Idempotency-Key -> recorded outcome, kept 24 hours (ADR 0011, ruling 6)
-- One row per accepted write. A replay of the same key returns response_status
-- and response_body and changes nothing; a different request under the same key
-- is refused (request_fingerprint). A failed request rolls its row back with
-- it: it was not accepted, so its retry runs normally.
--
-- The primary key is also what serialises a retry racing its original: the
-- second INSERT waits on the first's uncommitted row, then sees it.
CREATE TABLE watchlist_intention (
    user_id             uuid        NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    idempotency_key     uuid        NOT NULL,
    request_fingerprint text        NOT NULL,
    response_status     int         NOT NULL,
    response_body       jsonb,
    accepted_at         timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, idempotency_key)
);
--rollback DROP TABLE watchlist_intention;

--changeset hamza:0021-05-index-watchlist-intention-expiry
--comment: the purge of keys past their retention window
CREATE INDEX watchlist_intention_accepted_at_idx
    ON watchlist_intention (accepted_at);
--rollback DROP INDEX watchlist_intention_accepted_at_idx;
