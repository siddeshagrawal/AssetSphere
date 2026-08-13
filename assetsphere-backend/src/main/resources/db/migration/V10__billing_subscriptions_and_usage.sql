CREATE TABLE workspace_subscriptions (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    plan VARCHAR(16) NOT NULL CHECK (plan IN ('FREE', 'PRO')),
    status VARCHAR(32) NOT NULL CHECK (status IN ('ACTIVE', 'PAST_DUE', 'CANCELED')),
    payment_provider VARCHAR(32),
    external_subscription_id VARCHAR(255),
    current_period_start TIMESTAMPTZ NOT NULL,
    current_period_end TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL,
    CONSTRAINT uk_workspace_subscriptions_workspace UNIQUE (workspace_id)
);

INSERT INTO workspace_subscriptions (
    id, workspace_id, plan, status, current_period_start, current_period_end,
    created_at, updated_at, version
)
SELECT gen_random_uuid(), id, 'FREE', 'ACTIVE', date_trunc('month', now()),
       date_trunc('month', now()) + INTERVAL '1 month', now(), now(), 0
FROM workspaces;

CREATE TABLE billing_usage (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    metric VARCHAR(32) NOT NULL CHECK (metric IN ('AI_INSIGHT', 'ASK', 'EVOLUTION')),
    period_start DATE NOT NULL,
    usage_count BIGINT NOT NULL DEFAULT 0 CHECK (usage_count >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_billing_usage_period UNIQUE (workspace_id, metric, period_start)
);

CREATE INDEX idx_billing_usage_workspace_period ON billing_usage(workspace_id, period_start);

CREATE TABLE billing_usage_events (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    metric VARCHAR(32) NOT NULL CHECK (metric IN ('AI_INSIGHT', 'ASK', 'EVOLUTION')),
    operation_id UUID NOT NULL,
    period_start DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_billing_usage_event UNIQUE (workspace_id, metric, operation_id)
);
