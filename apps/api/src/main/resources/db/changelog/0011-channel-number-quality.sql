--liquibase formatted sql
--
-- What the provider says about a channel, beyond its name (contract M3, M4).

--changeset hamza:0011-01-add-channel-number
--comment: the number the provider assigns -- tvg-chno in an M3U, num in Xtream
-- NOT `position`, which sits two columns away and is a display index reassigned
-- at every ingestion. The two diverge the moment a channel leaves the playlist,
-- and it is this one the user knows by heart and types on a remote control.
-- Nullable, and null is the common case: many playlists carry no number at all.
ALTER TABLE channel
    ADD COLUMN number int;
--rollback ALTER TABLE channel DROP COLUMN number;

--changeset hamza:0011-02-add-channel-quality
--comment: definition as the source advertises it, echoed verbatim
-- text, never an enum and never a lookup table. Sources write HD, FHD, UHD, 4K,
-- H265, and they write them where they like. A constrained column would force
-- the ingestion to file the unrecognised under some value, which is to say to
-- lie about it; an unrecognised string is merely a badge a client can ignore.
ALTER TABLE channel
    ADD COLUMN quality text;
--rollback ALTER TABLE channel DROP COLUMN quality;

--changeset hamza:0011-03-index-channel-number
--comment: direct number entry on a remote control is a lookup by (source, number)
CREATE INDEX channel_source_number_idx ON channel (source_id, number)
    WHERE number IS NOT NULL;
--rollback DROP INDEX channel_source_number_idx;
