UPDATE billing_payments
SET provider = 'RAZORPAY_LOCAL'
WHERE provider = 'RAZORPAY';

UPDATE workspace_subscriptions
SET payment_provider = 'RAZORPAY_LOCAL'
WHERE payment_provider = 'RAZORPAY';

UPDATE workspace_subscriptions
SET payment_provider = NULL
WHERE payment_provider IS NOT NULL
  AND btrim(payment_provider) = '';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM billing_payments
        WHERE provider NOT IN ('STRIPE', 'RAZORPAY_LOCAL')
    ) THEN
        RAISE EXCEPTION 'Unsupported billing_payments.provider value remains after provider normalization';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM workspace_subscriptions
        WHERE payment_provider IS NOT NULL
          AND payment_provider NOT IN ('STRIPE', 'RAZORPAY_LOCAL')
    ) THEN
        RAISE EXCEPTION 'Unsupported workspace_subscriptions.payment_provider value remains after provider normalization';
    END IF;
END $$;

ALTER TABLE billing_payments
    DROP CONSTRAINT IF EXISTS billing_payments_status_check;

ALTER TABLE billing_payments
    ADD CONSTRAINT billing_payments_status_check
        CHECK (status IN ('CREATED', 'ORDER_CREATED', 'PAID', 'FAILED', 'CANCELED'));

ALTER TABLE billing_payments
    ADD CONSTRAINT billing_payments_provider_check
        CHECK (provider IN ('STRIPE', 'RAZORPAY_LOCAL'));

ALTER TABLE workspace_subscriptions
    ADD CONSTRAINT workspace_subscriptions_payment_provider_check
        CHECK (payment_provider IS NULL OR payment_provider IN ('STRIPE', 'RAZORPAY_LOCAL'));
