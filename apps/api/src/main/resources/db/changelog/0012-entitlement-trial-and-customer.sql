--liquibase formatted sql
--
-- The trial state, and the billing customer a portal session needs (contract G2, G3).

--changeset hamza:0012-01-add-entitlement-trial-ends-at
--comment: end of the free trial; non-null only while status is TRIALING
-- Distinct from current_period_end, which dates the end of a period that was
-- PAID FOR. "Votre essai se termine dans 3 jours" is an invitation to enter a
-- card; "renouvele le 14" is a reassurance. One column cannot produce both
-- screens.
ALTER TABLE entitlement
    ADD COLUMN trial_ends_at timestamptz;
--rollback ALTER TABLE entitlement DROP COLUMN trial_ends_at;

--changeset hamza:0012-02-entitlement-status-trialing
--comment: TRIALING joins the allowed states
-- Dropped and recreated rather than altered: PostgreSQL has no ALTER CONSTRAINT
-- for a CHECK, and a NOT VALID / VALIDATE pair buys nothing on a table this
-- size.
ALTER TABLE entitlement
    DROP CONSTRAINT entitlement_status_check;
ALTER TABLE entitlement
    ADD CONSTRAINT entitlement_status_check
        CHECK (status IN ('ACTIVE', 'TRIALING', 'PAST_DUE', 'CANCELED', 'EXPIRED'));
--rollback ALTER TABLE entitlement DROP CONSTRAINT entitlement_status_check;
--rollback ALTER TABLE entitlement ADD CONSTRAINT entitlement_status_check CHECK (status IN ('ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED'));

--changeset hamza:0012-03-add-entitlement-billing-customer-ref
--comment: the provider's customer identifier, distinct from provider_ref
-- provider_ref names the SUBSCRIPTION, which is what a webhook arrives keyed by.
-- This names the CUSTOMER, which is what opening the portal needs -- and the two
-- exist at different times: a user who abandoned a checkout has a customer and
-- no subscription. Folding them into one column would make
-- BILLING_CUSTOMER_NOT_FOUND unanswerable for exactly the person it is for.
ALTER TABLE entitlement
    ADD COLUMN billing_customer_ref text;
--rollback ALTER TABLE entitlement DROP COLUMN billing_customer_ref;
