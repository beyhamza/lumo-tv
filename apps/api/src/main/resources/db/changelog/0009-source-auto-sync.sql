--liquibase formatted sql

--changeset hamza:0009-01-add-source-auto-sync
--comment: whether the server re-synchronises a source on its own (contract M6)
-- On the source, not on the account and not on the device. Re-synchronising is
-- server work that hits the user's own IPTV server, so the decision belongs to
-- the source it hits: one may want a playlist that moves refreshed nightly and
-- a stable subscription left alone. Held on a device, the setting would have to
-- be made once per device and would still not describe what the server does
-- while every device is asleep.
--
-- NOT NULL DEFAULT true: a catalogue that silently goes stale is the failure a
-- user cannot diagnose, and existing rows predate the column, so the default is
-- what they get.
ALTER TABLE source
    ADD COLUMN auto_sync boolean NOT NULL DEFAULT true;
--rollback ALTER TABLE source DROP COLUMN auto_sync;
