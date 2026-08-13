CREATE TABLE oauth_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_oauth_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uk_oauth_user_provider UNIQUE (user_id, provider)
);

CREATE TABLE oauth_login_tickets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    ticket_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_oauth_login_ticket_expiry ON oauth_login_tickets(expires_at);

ALTER TABLE workspace_invitations ADD COLUMN invited_by_email VARCHAR(320);
ALTER TABLE workspace_invitations DROP CONSTRAINT IF EXISTS workspace_invitations_status_check;
ALTER TABLE workspace_invitations ADD CONSTRAINT workspace_invitations_status_check
    CHECK (status IN ('PENDING','ACCEPTED','DECLINED','EXPIRED','REVOKED'));
