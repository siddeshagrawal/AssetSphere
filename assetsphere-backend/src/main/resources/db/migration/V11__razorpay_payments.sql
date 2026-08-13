CREATE TABLE billing_payments (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    user_id UUID NOT NULL,
    requested_plan VARCHAR(16) NOT NULL CHECK (requested_plan = 'PRO'),
    provider VARCHAR(32) NOT NULL,
    provider_order_id VARCHAR(255),
    provider_payment_id VARCHAR(255),
    idempotency_key VARCHAR(128) NOT NULL,
    receipt VARCHAR(64) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('CREATED', 'ORDER_CREATED', 'PAID', 'FAILED')),
    failure_code VARCHAR(128),
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL,
    CONSTRAINT uk_billing_payment_idempotency UNIQUE (workspace_id, idempotency_key),
    CONSTRAINT uk_billing_payment_receipt UNIQUE (receipt),
    CONSTRAINT uk_billing_payment_provider_order UNIQUE (provider, provider_order_id),
    CONSTRAINT uk_billing_payment_provider_payment UNIQUE (provider, provider_payment_id)
);

CREATE INDEX idx_billing_payments_workspace_created ON billing_payments(workspace_id, created_at DESC);

CREATE TABLE billing_webhook_events (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('RECEIVED', 'PROCESSED', 'IGNORED')),
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    CONSTRAINT uk_billing_webhook_provider_event UNIQUE (provider, provider_event_id)
);
