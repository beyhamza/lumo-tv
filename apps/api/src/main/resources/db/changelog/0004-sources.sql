--liquibase formatted sql

--changeset hamza:0004-01-create-source
--comment: user IPTV sources; Xtream credentials encrypted at rest (AES-256-GCM)
CREATE TABLE source (
    id                 uuid        PRIMARY KEY,
    user_id            uuid        NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    label              text        NOT NULL,
    kind               text        NOT NULL,
    host               text,
    username           text,
    -- Envelope encryption: ciphertext + GCM nonce + tag, prefixed by the
    -- wrapped data key. The master key lives outside the database (environment
    -- variable in development, KMS in production), so a database dump alone
    -- decrypts nothing.
    password_encrypted bytea,
    m3u_url            text,
    epg_url            text,
    status             text        NOT NULL DEFAULT 'PENDING',
    -- A stable code, never a free-form message: the client owns the wording,
    -- in FR and EN.
    error_code         text,
    last_synced_at     timestamptz,
    expires_at         timestamptz,
    max_connections    int,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT source_kind_check   CHECK (kind IN ('M3U_URL', 'M3U_FILE', 'XTREAM')),
    CONSTRAINT source_status_check CHECK (status IN ('PENDING', 'SYNCING', 'READY', 'ERROR')),
    CONSTRAINT source_error_code_check CHECK (error_code IS NULL OR error_code IN (
        'SOURCE_UNREACHABLE', 'SOURCE_AUTH_FAILED', 'SOURCE_EXPIRED',
        'SOURCE_MAX_CONNECTIONS', 'SOURCE_INVALID_FORMAT', 'SOURCE_EMPTY',
        'SOURCE_TOO_LARGE')),
    -- An XTREAM source without a host is unusable; an M3U_URL source without a
    -- URL likewise. Enforced here so no code path can persist a half-source.
    CONSTRAINT source_kind_fields_check CHECK (
        (kind = 'XTREAM'  AND host IS NOT NULL AND username IS NOT NULL)
     OR (kind = 'M3U_URL' AND m3u_url IS NOT NULL)
     OR (kind = 'M3U_FILE')
    )
);
--rollback DROP TABLE source;

--changeset hamza:0004-02-index-source-user
--comment: every source query filters on user_id (docs/architecture.md §2)
CREATE INDEX source_user_id_idx ON source (user_id);
--rollback DROP INDEX source_user_id_idx;

--changeset hamza:0004-03-index-source-syncing
--comment: finds work in progress and syncs orphaned by a restart
CREATE INDEX source_status_idx ON source (status) WHERE status IN ('PENDING', 'SYNCING');
--rollback DROP INDEX source_status_idx;
