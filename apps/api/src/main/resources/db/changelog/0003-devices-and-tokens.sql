--liquibase formatted sql

--changeset hamza:0003-01-create-device
--comment: one installation of one application, linked to an account
CREATE TABLE device (
    id           uuid        PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    platform     text        NOT NULL,
    name         text,
    model        text,
    app_version  text,
    last_seen_at timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT device_platform_check
        CHECK (platform IN ('ANDROID_MOBILE', 'ANDROID_TV', 'WEB'))
);
--rollback DROP TABLE device;

--changeset hamza:0003-02-index-device-user
CREATE INDEX device_user_id_idx ON device (user_id, last_seen_at DESC);
--rollback DROP INDEX device_user_id_idx;

--changeset hamza:0003-03-create-refresh-token
--comment: opaque refresh tokens, stored hashed, rotated on every use
--comment: replaced_by chains a rotation so reuse detection can revoke the whole chain
CREATE TABLE refresh_token (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    device_id   uuid        NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    token_hash  text        NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    replaced_by uuid        REFERENCES refresh_token(id) ON DELETE SET NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);
--rollback DROP TABLE refresh_token;

--changeset hamza:0003-04-unique-refresh-token-hash
--comment: the hash IS the lookup key; the plaintext never touches the database
CREATE UNIQUE INDEX refresh_token_hash_key ON refresh_token (token_hash);
--rollback DROP INDEX refresh_token_hash_key;

--changeset hamza:0003-05-index-refresh-token-device
--comment: revoking a whole device chain reads by device_id, and it must be fast:
--comment: it runs on the theft-detection path
CREATE INDEX refresh_token_device_id_idx ON refresh_token (device_id);
--rollback DROP INDEX refresh_token_device_id_idx;

--changeset hamza:0003-06-index-refresh-token-user
CREATE INDEX refresh_token_user_id_idx ON refresh_token (user_id);
--rollback DROP INDEX refresh_token_user_id_idx;

--changeset hamza:0003-07-create-device-authorization
--comment: RFC 8628 TV activation; device_code is stored hashed, user_code is displayed
CREATE TABLE device_authorization (
    id               uuid        PRIMARY KEY,
    device_code_hash text        NOT NULL,
    user_code        text        NOT NULL,
    platform         text        NOT NULL,
    name             text,
    model            text,
    app_version      text,
    status           text        NOT NULL DEFAULT 'PENDING',
    user_id          uuid        REFERENCES "user"(id) ON DELETE CASCADE,
    expires_at       timestamptz NOT NULL,
    interval_seconds int         NOT NULL DEFAULT 5,
    last_polled_at   timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT device_authorization_status_check
        CHECK (status IN ('PENDING', 'APPROVED', 'DENIED', 'EXPIRED', 'CONSUMED')),
    CONSTRAINT device_authorization_platform_check
        CHECK (platform IN ('ANDROID_MOBILE', 'ANDROID_TV', 'WEB')),
    CONSTRAINT device_authorization_user_code_length_check
        CHECK (char_length(user_code) = 8)
);
--rollback DROP TABLE device_authorization;

--changeset hamza:0003-08-unique-device-authorization-code-hash
CREATE UNIQUE INDEX device_authorization_code_hash_key
    ON device_authorization (device_code_hash);
--rollback DROP INDEX device_authorization_code_hash_key;

--changeset hamza:0003-09-unique-pending-user-code
--comment: partial index: a user_code must be unique only while it can still be
--comment: approved. Expired and consumed rows keep theirs, so the short 8-character
--comment: alphabet is not exhausted by history.
CREATE UNIQUE INDEX device_authorization_pending_user_code_key
    ON device_authorization (user_code)
    WHERE status = 'PENDING';
--rollback DROP INDEX device_authorization_pending_user_code_key;

--changeset hamza:0003-10-index-device-authorization-expiry
--comment: drives the sweep that expires stale authorizations
CREATE INDEX device_authorization_expires_at_idx
    ON device_authorization (expires_at)
    WHERE status = 'PENDING';
--rollback DROP INDEX device_authorization_expires_at_idx;
