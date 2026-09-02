--liquibase formatted sql

--changeset hamza:0019-01-episode-audio-codec
--comment The audio codec of an episode, so a client can warn before playing.
--
-- A browser decodes picture and sound separately, and none of them ships a Dolby
-- Digital decoder — Dolby is licensed and Chromium does not pay it. An episode in
-- `ac3` therefore plays perfectly and silently, behind a mute button that does
-- nothing when pressed. Measured on a real catalogue by sampling six series out
-- of 49 437: roughly a third of the episodes carry AC-3 or E-AC-3 as their only
-- audio track.
--
-- The web client already says so once it has noticed. Noticing takes three
-- seconds of playing, because the only reading a browser offers is a count of
-- decoded bytes. With this column it can say so on the episode, before anybody
-- presses anything.
--
-- **Echoed verbatim, never interpreted.** `ac3`, `eac3`, `aac`, `dts` — whatever
-- the panel calls it, for the reason `channel.quality` is not normalised either:
-- deciding what a provider meant is not this layer's decision. And which codecs
-- a player handles is the player's business, not a column's — it differs between
-- a browser and a television, and it changes with the year.
--
-- **Nullable, and null means "not known", never "no audio".** Panels state it
-- inconsistently, and every episode already ingested has none until the next
-- synchronisation. A client that reads null says nothing, which is exactly what
-- it did before this column existed.
--
-- Not backfilled, and could not be: the value only exists in a `get_series_info`
-- answer, one HTTP call per series against somebody else's machine. It arrives
-- when a tree is next fetched, at no extra cost, because it is already in the
-- sheet the tree comes from.
ALTER TABLE episode ADD COLUMN audio_codec text;

--rollback ALTER TABLE episode DROP COLUMN audio_codec;

--changeset hamza:0019-02-episode-audio-channels
--comment The channel count of that track, the other half of the same sentence.
--
-- `2`, `6`, or null. Beside the codec rather than derived from it — stereo AC-3
-- and 5.1 AC-3 are both common, and a client that wants to write "Dolby Digital
-- 5.1" should not have to guess the number.
ALTER TABLE episode ADD COLUMN audio_channels int;

--rollback ALTER TABLE episode DROP COLUMN audio_channels;
