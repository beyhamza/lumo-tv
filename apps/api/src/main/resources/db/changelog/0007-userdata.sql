--liquibase formatted sql
--
-- Favourites and playback progress. NOT implemented in sprint 1: the tables
-- exist so the schema is complete and so a favourite can be created by a later
-- sprint without a migration on live data, but no endpoint writes them yet.

--changeset hamza:0007-01-create-favorite-group
--comment: user-defined groupings; a default group is created on the first add
CREATE TABLE favorite_group (
    id       uuid PRIMARY KEY,
    user_id  uuid NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    name     text NOT NULL,
    position int  NOT NULL DEFAULT 0
);
--rollback DROP TABLE favorite_group;

--changeset hamza:0007-02-index-favorite-group-user
CREATE INDEX favorite_group_user_id_idx ON favorite_group (user_id, position);
--rollback DROP INDEX favorite_group_user_id_idx;

--changeset hamza:0007-03-unique-favorite-group-name
CREATE UNIQUE INDEX favorite_group_user_name_key ON favorite_group (user_id, name);
--rollback DROP INDEX favorite_group_user_name_key;

--changeset hamza:0007-04-create-favorite
--comment: user_id is denormalised from favorite_group on purpose: every query in
--comment: this codebase filters on user_id (docs/architecture.md §2), and a rule
--comment: that needs a join to be satisfied is a rule that eventually gets skipped
CREATE TABLE favorite (
    id         uuid PRIMARY KEY,
    group_id   uuid NOT NULL REFERENCES favorite_group(id) ON DELETE CASCADE,
    user_id    uuid NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    source_id  uuid NOT NULL REFERENCES source(id) ON DELETE CASCADE,
    channel_id uuid NOT NULL REFERENCES channel(id) ON DELETE CASCADE,
    position   int  NOT NULL DEFAULT 0
);
--rollback DROP TABLE favorite;

--changeset hamza:0007-05-index-favorite-user
CREATE INDEX favorite_user_id_idx ON favorite (user_id, group_id, position);
--rollback DROP INDEX favorite_user_id_idx;

--changeset hamza:0007-06-unique-favorite-channel
--comment: a channel appears at most once per group
CREATE UNIQUE INDEX favorite_group_channel_key ON favorite (group_id, channel_id);
--rollback DROP INDEX favorite_group_channel_key;

--changeset hamza:0007-07-create-playback-progress
--comment: VOD and episodes only; live has no progress
CREATE TABLE playback_progress (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    item_type   text        NOT NULL,
    item_ref    text        NOT NULL,
    position_ms bigint      NOT NULL,
    duration_ms bigint,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT playback_progress_item_type_check CHECK (item_type IN ('VOD', 'EPISODE')),
    CONSTRAINT playback_progress_position_check  CHECK (position_ms >= 0),
    CONSTRAINT playback_progress_duration_check  CHECK (duration_ms IS NULL OR duration_ms >= 0)
);
--rollback DROP TABLE playback_progress;

--changeset hamza:0007-08-unique-playback-progress
--comment: PUT /me/progress is an upsert keyed on this triple
CREATE UNIQUE INDEX playback_progress_item_key
    ON playback_progress (user_id, item_type, item_ref);
--rollback DROP INDEX playback_progress_item_key;
