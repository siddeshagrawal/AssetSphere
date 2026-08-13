CREATE TABLE asset_intelligence (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    asset_id UUID NOT NULL REFERENCES assets(id),
    asset_version_id UUID NOT NULL REFERENCES asset_versions(id),
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED', 'NOT_APPLICABLE', 'DISABLED')),
    provider VARCHAR(32),
    model VARCHAR(128),
    summary TEXT,
    key_points TEXT NOT NULL DEFAULT '[]',
    tags TEXT NOT NULL DEFAULT '[]',
    input_characters INTEGER NOT NULL DEFAULT 0 CHECK (input_characters >= 0),
    input_truncated BOOLEAN NOT NULL DEFAULT FALSE,
    failure_code VARCHAR(64),
    failure_message VARCHAR(1000),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL,
    CONSTRAINT uk_asset_intelligence_asset_version UNIQUE (asset_version_id)
);

CREATE INDEX idx_asset_intelligence_workspace_asset ON asset_intelligence(workspace_id, asset_id, created_at DESC);
