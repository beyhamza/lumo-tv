--liquibase formatted sql

--changeset hamza:0006-01-create-epg-programme
--comment: programme guide, joined to channels by tvg_id within a source
CREATE TABLE epg_programme (
    id          uuid        PRIMARY KEY,
    source_id   uuid        NOT NULL REFERENCES source(id) ON DELETE CASCADE,
    tvg_id      text        NOT NULL,
    starts_at   timestamptz NOT NULL,
    ends_at     timestamptz NOT NULL,
    title       text        NOT NULL,
    description text,
    category    text,
    CONSTRAINT epg_programme_range_check CHECK (ends_at > starts_at)
);
--rollback DROP TABLE epg_programme;

--changeset hamza:0006-02-unique-epg-programme
--comment: (source_id, tvg_id, starts_at) is both the natural key AND the only
--comment: read pattern ("what is on this channel between X and Y"), so one unique
--comment: index serves both. XMLTV files are re-fetched whole and overlap heavily
--comment: between runs; this key makes re-ingestion an idempotent upsert instead of
--comment: a table that doubles in size on every sync.
CREATE UNIQUE INDEX epg_programme_natural_key
    ON epg_programme (source_id, tvg_id, starts_at);
--rollback DROP INDEX epg_programme_natural_key;

--changeset hamza:0006-03-index-epg-retention
--comment: drives the D-1/D+3 retention purge
CREATE INDEX epg_programme_ends_at_idx ON epg_programme (ends_at);
--rollback DROP INDEX epg_programme_ends_at_idx;
