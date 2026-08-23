--liquibase formatted sql

--changeset hamza:0001-01-extension-citext
--comment: case-insensitive text, used for user.email so Foo@x.com and foo@x.com are one account
CREATE EXTENSION IF NOT EXISTS citext;
--rollback DROP EXTENSION IF EXISTS citext;

--changeset hamza:0001-02-extension-pg-trgm
--comment: trigram index support for typo-tolerant channel search (ADR 0002)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
--rollback DROP EXTENSION IF EXISTS pg_trgm;
