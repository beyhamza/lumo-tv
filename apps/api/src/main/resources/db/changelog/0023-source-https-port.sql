--liquibase formatted sql

--changeset hamza:0023-01-source-https-port
--comment The https port an Xtream panel announces, so a browser can be offered it.
--
-- A page served over https cannot open an http stream, and most panels are typed
-- in as http. Some of them also serve https, and say so in `server_info.https_port`
-- on every authentication (ADR 0007 keeps playback direct: this changes which
-- address the browser opens, never who carries the bytes).
--
-- On `source` rather than in another `stream_url` column: it describes the panel,
-- not a channel, and the three catalogue tables would otherwise each hold a second
-- copy of the user's credentials.
--
-- **Nullable, and null means "none announced".** Not backfilled, and could not be:
-- the value only exists in the panel's answer. Every Xtream source has it from its
-- next synchronisation.
ALTER TABLE source ADD COLUMN https_port int;

--rollback ALTER TABLE source DROP COLUMN https_port;
