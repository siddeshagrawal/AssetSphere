ALTER TABLE billing_usage
    ALTER COLUMN period_start TYPE TIMESTAMPTZ
    USING period_start::timestamp AT TIME ZONE 'UTC';

ALTER TABLE billing_usage_events
    ALTER COLUMN period_start TYPE TIMESTAMPTZ
    USING period_start::timestamp AT TIME ZONE 'UTC';

ALTER TABLE workspace_subscriptions
    ADD COLUMN usage_period_start TIMESTAMPTZ;

UPDATE workspace_subscriptions
SET usage_period_start = current_period_start;

ALTER TABLE workspace_subscriptions
    ALTER COLUMN usage_period_start SET NOT NULL;

CREATE TABLE billing_provider_event_state (
    provider VARCHAR(32) NOT NULL,
    provider_identity VARCHAR(320) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    event_priority INTEGER NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (provider, provider_identity)
);
