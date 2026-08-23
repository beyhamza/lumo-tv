--liquibase formatted sql
--
-- Access rights. NOT implemented in sprint 1: no endpoint reads or writes this
-- table yet. It exists because it is the single source of truth for entitlements
-- (ADR 0003) and the schema is described in full by docs/domain-model.md §2.

--changeset hamza:0008-01-create-entitlement
--comment: server-side access rights; fed by Stripe webhooks in v1, Play RTDN in v2
CREATE TABLE entitlement (
    id                 uuid        PRIMARY KEY,
    user_id            uuid        NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    plan               text        NOT NULL DEFAULT 'FREE',
    status             text        NOT NULL DEFAULT 'ACTIVE',
    provider           text        NOT NULL DEFAULT 'MANUAL',
    provider_ref       text,
    current_period_end timestamptz,
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT entitlement_plan_check     CHECK (plan IN ('FREE', 'PREMIUM')),
    CONSTRAINT entitlement_status_check   CHECK (status IN ('ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED')),
    CONSTRAINT entitlement_provider_check CHECK (provider IN ('STRIPE', 'PLAY', 'MANUAL'))
);
--rollback DROP TABLE entitlement;

--changeset hamza:0008-02-unique-entitlement-user
--comment: "one active entitlement per user" (docs/domain-model.md §2), enforced by
--comment: the database rather than by whichever webhook handler happens to run
CREATE UNIQUE INDEX entitlement_user_id_key ON entitlement (user_id);
--rollback DROP INDEX entitlement_user_id_key;

--changeset hamza:0008-03-index-entitlement-provider-ref
--comment: Stripe webhooks arrive keyed by subscription reference, not by user
CREATE INDEX entitlement_provider_ref_idx ON entitlement (provider, provider_ref)
    WHERE provider_ref IS NOT NULL;
--rollback DROP INDEX entitlement_provider_ref_idx;
