--liquibase formatted sql
--
-- Series, seasons and episodes: the first three-level object in this schema.
--
-- A film is a row. A series is a tree, and the three levels do not arrive at the
-- same moment: the series list comes from `get_series` at synchronisation, while
-- seasons and episodes come from `get_series_info` — one call to the user's own
-- server, per series, made when somebody opens one.
--
-- That split is why `series` carries a cache stamp and the other two do not.

--changeset hamza:0016-01-create-series
--comment: a vod_item minus container_extension (a series is not played) plus an
--comment: indicative episode_run_time. tree_fetched_at is the cache stamp; there
--comment: is deliberately no boolean beside it — see 0016-04.
CREATE TABLE series (
    id               uuid    PRIMARY KEY,
    source_id        uuid    NOT NULL REFERENCES source(id) ON DELETE CASCADE,
    category_id      uuid    REFERENCES category(id) ON DELETE SET NULL,
    external_id      text,
    name             text    NOT NULL,
    poster_url       text,
    year             int,
    -- What the panel says a typical episode lasts, in minutes. Indicative, and
    -- never used as a duration: that is episode.duration_seconds.
    episode_run_time int,
    rating           text,
    plot             text,
    -- When the tree was last fetched from the provider. Unlike a film's synopsis
    -- this expires: a series in production gains an episode a week.
    tree_fetched_at  timestamptz,
    position         int     NOT NULL DEFAULT 0,
    is_adult         boolean NOT NULL DEFAULT false
);
--rollback DROP TABLE series;

--changeset hamza:0016-02-create-season
--comment: no external identifier, because panels frequently do not mint one for
--comment: a season. Its identity is its number within its series — see 0016-06.
CREATE TABLE season (
    id            uuid PRIMARY KEY,
    series_id     uuid NOT NULL REFERENCES series(id) ON DELETE CASCADE,
    season_number int  NOT NULL,
    -- What the panel claims. It can disagree with the episodes actually listed,
    -- and when it does the list is what a client counts.
    episode_count int,
    poster_url    text
);
--rollback DROP TABLE season;

--changeset hamza:0016-03-create-episode
--comment: stream_url is sensitive and never leaves the owner's context, exactly
--comment: as on channel and vod_item. container_extension is always present here
--comment: (there is no M3U series — ADR 0010) but stays nullable for the same
--comment: reason the column exists on vod_item: a NOT NULL that a provider can
--comment: violate is an ingestion that fails on one bad row.
CREATE TABLE episode (
    id                  uuid    PRIMARY KEY,
    series_id           uuid    NOT NULL REFERENCES series(id) ON DELETE CASCADE,
    season_id           uuid    NOT NULL REFERENCES season(id) ON DELETE CASCADE,
    -- Denormalised from series, and deliberately: every ownership check and every
    -- resolve-by-ids query filters on the source, and joining two levels up on
    -- each of them buys nothing. It cannot drift — an episode never changes
    -- series.
    source_id           uuid    NOT NULL REFERENCES source(id) ON DELETE CASCADE,
    external_id         text,
    season_number       int     NOT NULL,
    episode_number      int     NOT NULL,
    name                text,
    -- This episode's own length. What a client compares a saved position against.
    duration_seconds    bigint,
    plot                text,
    -- Never selected by a listing query. Read only by
    -- GET /episodes/{id}/playback, one row at a time, after an ownership check.
    stream_url          text    NOT NULL,
    container_extension text
);
--rollback DROP TABLE episode;

--changeset hamza:0016-04-unique-series-external-id
--comment: the upsert key, and the same one channel and vod_item use. It is what
--comment: makes a re-synchronisation an upsert rather than a wipe, so a saved
--comment: position keeps pointing at something real after the weekly pass.
CREATE UNIQUE INDEX series_source_external_id_key
    ON series (source_id, external_id)
    WHERE external_id IS NOT NULL;
--rollback DROP INDEX series_source_external_id_key;

--changeset hamza:0016-05-index-series-listing
--comment: the ordering the listing endpoint reads in
CREATE INDEX series_source_category_position_idx
    ON series (source_id, category_id, position);
--rollback DROP INDEX series_source_category_position_idx;

--changeset hamza:0016-06-index-series-name-trigram
--comment: same reasoning as channel and vod_item: an ILIKE '%q%' without an index
--comment: is a sequential scan, and a series catalogue is counted in hundreds to
--comment: thousands.
CREATE INDEX series_name_trgm_idx ON series USING gin (name gin_trgm_ops);
--rollback DROP INDEX series_name_trgm_idx;

--changeset hamza:0016-07-unique-season-number
--comment: the upsert key is the NUMBER, not an identifier, because a panel does
--comment: not always mint one for a season. Season 2 of a series is season 2 of
--comment: that series, and there is exactly one.
CREATE UNIQUE INDEX season_series_number_key ON season (series_id, season_number);
--rollback DROP INDEX season_series_number_key;

--changeset hamza:0016-08-unique-episode-external-id
--comment: the upsert key is the panel's episode identifier, which unlike a
--comment: season's always exists: it is what the playback URL is built from. It
--comment: is scoped to the series rather than to the source, because that is the
--comment: level at which a provider guarantees it.
CREATE UNIQUE INDEX episode_series_external_id_key
    ON episode (series_id, external_id)
    WHERE external_id IS NOT NULL;
--rollback DROP INDEX episode_series_external_id_key;

--changeset hamza:0016-09-index-episode-tree
--comment: the order GET /series/{id} reads the tree in, in one pass
CREATE INDEX episode_series_order_idx
    ON episode (series_id, season_number, episode_number);
--rollback DROP INDEX episode_series_order_idx;

--changeset hamza:0016-10-index-episode-source
--comment: GET /sources/{id}/episodes?ids= filters on the source before anything
--comment: else, which is also the ownership check
CREATE INDEX episode_source_idx ON episode (source_id);
--rollback DROP INDEX episode_source_idx;
