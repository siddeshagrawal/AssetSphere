ALTER TABLE outbox_events
    ADD COLUMN claim_owner VARCHAR(128),
    ADD COLUMN claimed_at TIMESTAMPTZ;

CREATE INDEX idx_outbox_claimable
    ON outbox_events(status, next_attempt_at, claimed_at, created_at);
