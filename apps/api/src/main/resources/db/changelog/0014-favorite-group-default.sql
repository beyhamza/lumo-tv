--liquibase formatted sql
--
-- The default favourite group gains a flag of its own.
--
-- Until now the group created on the first add was identified by its name,
-- 'Favorites', both in the ON CONFLICT of the upsert and in the SELECT that read
-- it back. That works exactly until somebody renames it: the next add finds no
-- group by that name and creates a second one. The flag survives a rename; a
-- name never could.

--changeset hamza:0014-01-add-favorite-group-is-default
ALTER TABLE favorite_group ADD COLUMN is_default boolean NOT NULL DEFAULT false;
--rollback ALTER TABLE favorite_group DROP COLUMN is_default;

--changeset hamza:0014-02-backfill-favorite-group-is-default
--comment: the only group that could have been created as the default is the one
--comment: the server named itself, so that name is the whole of the backfill
UPDATE favorite_group SET is_default = true WHERE name = 'Favorites';
--rollback UPDATE favorite_group SET is_default = false;

--changeset hamza:0014-03-unique-favorite-group-one-default
--comment: at most one default per account. A partial unique index rather than a
--comment: check: it is the only form that constrains across rows, and it is what
--comment: makes the first-add upsert safe when a phone and a television both
--comment: star something at the same moment.
CREATE UNIQUE INDEX favorite_group_one_default_key
    ON favorite_group (user_id)
    WHERE is_default;
--rollback DROP INDEX favorite_group_one_default_key;
