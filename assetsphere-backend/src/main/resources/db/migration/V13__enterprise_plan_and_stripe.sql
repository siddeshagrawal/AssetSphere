ALTER TABLE workspace_subscriptions DROP CONSTRAINT IF EXISTS workspace_subscriptions_plan_check;
ALTER TABLE workspace_subscriptions ADD CONSTRAINT workspace_subscriptions_plan_check
    CHECK (plan IN ('FREE', 'PRO', 'ENTERPRISE'));

ALTER TABLE billing_payments ADD COLUMN provider_checkout_url VARCHAR(2048);

ALTER TABLE asset_intelligence ADD COLUMN requested_model_id VARCHAR(128);
