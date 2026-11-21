--liquibase formatted sql

--changeset hamza:0024-01-user-is-admin
--comment Who reaches the administration screens (ADR 0015).
--
-- A column read on every request rather than a claim in the token: taking the
-- right away from an account has to work now, not in fifteen minutes. No endpoint
-- writes it. The first administrator is set by hand on the server
-- (docs/ops/deploy-vps.md).
ALTER TABLE "user" ADD COLUMN is_admin boolean NOT NULL DEFAULT false;

--rollback ALTER TABLE "user" DROP COLUMN is_admin;

--changeset hamza:0024-02-legal-document-version
--comment The versions of the legal documents (ADR 0015). A published text never changes.
--
-- Both languages in one row, because they are one version: publishing requires
-- both, and there is no state where FR is at version 3 and EN at version 2.
CREATE TABLE legal_document_version (
    id             uuid        PRIMARY KEY,
    document_type  text        NOT NULL,
    status         text        NOT NULL,
    version        int,
    title_fr       text        NOT NULL,
    title_en       text        NOT NULL,
    body_fr        text        NOT NULL,
    body_en        text        NOT NULL,
    change_summary text,
    published_at   timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT legal_document_version_type_check
        CHECK (document_type IN ('PRIVACY_POLICY', 'TERMS_OF_USE', 'TERMS_OF_SALE')),
    CONSTRAINT legal_document_version_status_check
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    -- A draft has no number yet: discarding one must not leave a gap.
    CONSTRAINT legal_document_version_published_fields_check
        CHECK ((status = 'DRAFT') = (version IS NULL)
           AND (status = 'DRAFT') = (published_at IS NULL)),
    CONSTRAINT legal_document_version_number_unique UNIQUE (document_type, version)
);

--rollback DROP TABLE legal_document_version;

--changeset hamza:0024-03-legal-one-draft-per-document
--comment At most one draft per document, enforced by the base and not by a read first.
CREATE UNIQUE INDEX legal_document_version_one_draft
    ON legal_document_version (document_type) WHERE status = 'DRAFT';

--rollback DROP INDEX legal_document_version_one_draft;

--changeset hamza:0024-04-legal-one-published-per-document
--comment At most one version in force per document.
CREATE UNIQUE INDEX legal_document_version_one_published
    ON legal_document_version (document_type) WHERE status = 'PUBLISHED';

--rollback DROP INDEX legal_document_version_one_published;

--changeset hamza:0024-05-app-settings
--comment The application's configuration: one row, typed columns, no key-value bag.
CREATE TABLE app_settings (
    singleton         boolean     PRIMARY KEY DEFAULT true,
    support_email     text,
    registration_open boolean     NOT NULL DEFAULT true,
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT app_settings_singleton_check CHECK (singleton)
);
INSERT INTO app_settings (singleton) VALUES (true);

--rollback DROP TABLE app_settings;
