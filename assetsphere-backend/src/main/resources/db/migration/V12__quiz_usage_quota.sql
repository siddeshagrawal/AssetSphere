ALTER TABLE billing_usage DROP CONSTRAINT IF EXISTS billing_usage_metric_check;
ALTER TABLE billing_usage ADD CONSTRAINT billing_usage_metric_check
    CHECK (metric IN ('AI_INSIGHT', 'ASK', 'EVOLUTION', 'QUIZ_GENERATION'));

ALTER TABLE billing_usage_events DROP CONSTRAINT IF EXISTS billing_usage_events_metric_check;
ALTER TABLE billing_usage_events ADD CONSTRAINT billing_usage_events_metric_check
    CHECK (metric IN ('AI_INSIGHT', 'ASK', 'EVOLUTION', 'QUIZ_GENERATION'));
