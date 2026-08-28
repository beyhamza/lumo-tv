--liquibase formatted sql
--
-- Films, and the source column the progress key was missing.
--
-- `vod_item` is `channel`'s sibling rather than its subclass: what a person needs
-- in order to *choose* a film — a poster, a year, a running time, a synopsis —
-- is six columns that would be null on the fifteen thousand rows of an ordinary
-- channel list.

--changeset hamza:0015-01-create-vod-item
--comment: stream_url is sensitive and never leaves the owner's context, exactly
--comment: as on channel; container_extension is null for M3U films (ADR 0009)
CREATE TABLE vod_item (
    id                  uuid    PRIMARY KEY,
    source_id           uuid    NOT NULL REFERENCES source(id) ON DELETE CASCADE,
    category_id         uuid    REFERENCES category(id) ON DELETE SET NULL,
    external_id         text,
    name                text    NOT NULL,
    poster_url          text,
    year                int,
    duration_seconds    int,
    rating              text,
    -- Fetched on demand, not at ingestion: get_vod_info is one HTTP call per
    -- film, and a catalogue of thirty thousand would be thirty thousand requests
    -- against the user's own provider at every synchronisation.
    plot                text,
    plot_fetched_at     timestamptz,
    -- Never selected by a listing query. Read only by GET /vod/{id}/playback,
    -- one row at a time, after an ownership check.
    stream_url          text    NOT NULL,
    -- The fragment an Xtream playback URL is built from. Null for an M3U film,
    -- whose playlist carries the complete URL (ADR 0009, ruling 4).
    container_extension text,
    position            int     NOT NULL DEFAULT 0,
    is_adult            boolean NOT NULL DEFAULT false
);
--rollback DROP TABLE vod_item;

--changeset hamza:0015-02-index-vod-item-listing
--comment: the ordering the listing endpoint reads in
CREATE INDEX vod_item_source_category_position_idx
    ON vod_item (source_id, category_id, position);
--rollback DROP INDEX vod_item_source_category_position_idx;

--changeset hamza:0015-03-index-vod-item-name-trigram
--comment: same reasoning as channel_name_trgm_idx. A film catalogue is commonly
--comment: three times the size of the channel list, so a sequential scan here
--comment: costs more than it does there.
CREATE INDEX vod_item_name_trgm_idx ON vod_item USING gin (name gin_trgm_ops);
--rollback DROP INDEX vod_item_name_trgm_idx;

--changeset hamza:0015-04-unique-vod-item-external-id
--comment: upsert key for re-ingestion, same reasoning as category and channel:
--comment: ids survive a sync, so a favourite or a progress row keeps pointing
--comment: at something real
CREATE UNIQUE INDEX vod_item_source_external_id_key
    ON vod_item (source_id, external_id)
    WHERE external_id IS NOT NULL;
--rollback DROP INDEX vod_item_source_external_id_key;

--changeset hamza:0015-05-add-playback-progress-source
--comment: item_ref is minted by the user's own panel and is opaque to us. Two
--comment: subscriptions can use 1042 for two different films, and without this
--comment: column the progress of one is served for the other — a film that
--comment: mysteriously resumes twenty minutes in.
ALTER TABLE playback_progress ADD COLUMN source_id uuid REFERENCES source(id) ON DELETE CASCADE;
--rollback ALTER TABLE playback_progress DROP COLUMN source_id;

--changeset hamza:0015-06-rekey-playback-progress
--comment: no backfill and none possible: an existing row cannot be attributed to
--comment: a source after the fact. There are none to lose — nothing has ever
--comment: called PUT /me/progress — and deleting is honest where guessing would
--comment: not be.
DELETE FROM playback_progress WHERE source_id IS NULL;
--rollback SELECT 1;

--changeset hamza:0015-07-playback-progress-source-not-null
ALTER TABLE playback_progress ALTER COLUMN source_id SET NOT NULL;
--rollback ALTER TABLE playback_progress ALTER COLUMN source_id DROP NOT NULL;

--changeset hamza:0015-08-unique-playback-progress-with-source
--comment: the upsert key gains the source. Dropped and recreated rather than
--comment: added beside: two unique indexes would let the same item exist twice.
DROP INDEX playback_progress_item_key;
CREATE UNIQUE INDEX playback_progress_item_key
    ON playback_progress (user_id, source_id, item_type, item_ref);
--rollback DROP INDEX playback_progress_item_key;
--rollback CREATE UNIQUE INDEX playback_progress_item_key ON playback_progress (user_id, item_type, item_ref);
