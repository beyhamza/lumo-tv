--liquibase formatted sql

--changeset hamza:0002-01-create-user
--comment: accounts; password_hash is null for SSO-only users
CREATE TABLE "user" (
    id                uuid        PRIMARY KEY,
    email             citext      NOT NULL,
    password_hash     text,
    display_name      text,
    locale            text        NOT NULL DEFAULT 'en',
    email_verified_at timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT user_locale_check CHECK (locale IN ('fr', 'en'))
);
--rollback DROP TABLE "user";

--changeset hamza:0002-02-unique-user-email
--comment: one account per email; citext makes this case-insensitive
CREATE UNIQUE INDEX user_email_key ON "user" (email);
--rollback DROP INDEX user_email_key;

--changeset hamza:0002-03-create-oauth-identity
--comment: SSO identities; a second provider attaches to the SAME user, never a new one
CREATE TABLE oauth_identity (
    id               uuid        PRIMARY KEY,
    user_id          uuid        NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    provider         text        NOT NULL,
    provider_user_id text        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT oauth_identity_provider_check CHECK (provider IN ('GOOGLE', 'APPLE'))
);
--rollback DROP TABLE oauth_identity;

--changeset hamza:0002-04-unique-oauth-identity
--comment: a provider account maps to exactly one Lumo account
CREATE UNIQUE INDEX oauth_identity_provider_key ON oauth_identity (provider, provider_user_id);
--rollback DROP INDEX oauth_identity_provider_key;

--changeset hamza:0002-05-index-oauth-identity-user
CREATE INDEX oauth_identity_user_id_idx ON oauth_identity (user_id);
--rollback DROP INDEX oauth_identity_user_id_idx;

--changeset hamza:0002-06-create-user-token
--comment: single-use email-verification and password-reset tokens, stored hashed
--comment: NOT in docs/domain-model.md §2 - added because /auth/verify-email and
--comment: /auth/password/reset in §3 have nowhere else to keep their token
CREATE TABLE user_token (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    purpose    text        NOT NULL,
    token_hash text        NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT user_token_purpose_check
        CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET'))
);
--rollback DROP TABLE user_token;

--changeset hamza:0002-07-unique-user-token-hash
--comment: the hash is the lookup key; the plaintext exists only in the email
CREATE UNIQUE INDEX user_token_hash_key ON user_token (token_hash);
--rollback DROP INDEX user_token_hash_key;

--changeset hamza:0002-08-index-user-token-user
CREATE INDEX user_token_user_id_purpose_idx ON user_token (user_id, purpose);
--rollback DROP INDEX user_token_user_id_purpose_idx;
