--liquibase formatted sql

--changeset hamza:0005-01-create-category
--comment: channel groupings within a source; channels with no group-title land in one of these
CREATE TABLE category (
    id           uuid   PRIMARY KEY,
    source_id    uuid   NOT NULL REFERENCES source(id) ON DELETE CASCADE,
    external_id  text,
    name         text   NOT NULL,
    content_type text   NOT NULL,
    position     int    NOT NULL DEFAULT 0,
    CONSTRAINT category_content_type_check CHECK (content_type IN ('LIVE', 'VOD', 'SERIES'))
);
--rollback DROP TABLE category;

--changeset hamza:0005-02-index-category-source
CREATE INDEX category_source_id_idx ON category (source_id, content_type, position);
--rollback DROP INDEX category_source_id_idx;

--changeset hamza:0005-03-unique-category-external-id
--comment: makes re-ingestion an upsert instead of a delete-and-reinsert, so
--comment: category ids survive a sync and favourites keep pointing somewhere real
CREATE UNIQUE INDEX category_source_external_id_key
    ON category (source_id, content_type, external_id)
    WHERE external_id IS NOT NULL;
--rollback DROP INDEX category_source_external_id_key;

--changeset hamza:0005-04-create-channel
--comment: live channels; stream_url is sensitive and never leaves the owner's context
CREATE TABLE channel (
    id          uuid    PRIMARY KEY,
    source_id   uuid    NOT NULL REFERENCES source(id) ON DELETE CASCADE,
    category_id uuid    REFERENCES category(id) ON DELETE SET NULL,
    external_id text,
    name        text    NOT NULL,
    logo_url    text,
    tvg_id      text,
    -- Never selected by a listing query. Read only by GET /channels/{id}/playback,
    -- one row at a time, after an ownership check.
    stream_url  text    NOT NULL,
    position    int     NOT NULL DEFAULT 0,
    is_adult    boolean NOT NULL DEFAULT false
);
--rollback DROP TABLE channel;

--changeset hamza:0005-05-index-channel-listing
--comment: the ordering the listing endpoint reads in
CREATE INDEX channel_source_category_position_idx
    ON channel (source_id, category_id, position);
--rollback DROP INDEX channel_source_category_position_idx;

--changeset hamza:0005-06-index-channel-name-trigram
--comment: typo-tolerant search (ADR 0002). GIN + gin_trgm_ops answers ILIKE '%q%'
--comment: without a sequential scan over a 15 000-row catalogue.
CREATE INDEX channel_name_trgm_idx ON channel USING gin (name gin_trgm_ops);
--rollback DROP INDEX channel_name_trgm_idx;

--changeset hamza:0005-07-unique-channel-external-id
--comment: upsert key for re-ingestion, same reasoning as category
CREATE UNIQUE INDEX channel_source_external_id_key
    ON channel (source_id, external_id)
    WHERE external_id IS NOT NULL;
--rollback DROP INDEX channel_source_external_id_key;
