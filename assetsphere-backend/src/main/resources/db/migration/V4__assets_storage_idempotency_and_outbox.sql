CREATE TABLE storage_objects (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    checksum_sha256 VARCHAR(64) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    storage_provider VARCHAR(32) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0),
    mime_type VARCHAR(255) NOT NULL,
    reference_count INTEGER NOT NULL DEFAULT 1 CHECK (reference_count > 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL,
    CONSTRAINT uk_storage_object_workspace_checksum UNIQUE (workspace_id, checksum_sha256)
);
CREATE INDEX idx_storage_objects_workspace_checksum ON storage_objects(workspace_id, checksum_sha256);

CREATE TABLE assets (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    owner_user_id UUID NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    asset_type VARCHAR(32) NOT NULL CHECK (asset_type IN ('PDF','DOCX','IMAGE','OTHER')),
    lifecycle_status VARCHAR(32) NOT NULL CHECK (lifecycle_status IN ('ACTIVE','ARCHIVED','DELETED')),
    processing_status VARCHAR(32) NOT NULL CHECK (processing_status IN ('UPLOADED','QUEUED','PROCESSING','READY','PARTIALLY_PROCESSED','FAILED')),
    latest_version_number INTEGER NOT NULL CHECK (latest_version_number > 0),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL
);
CREATE INDEX idx_assets_workspace_created ON assets(workspace_id, created_at DESC);
CREATE INDEX idx_assets_workspace_processing ON assets(workspace_id, processing_status);

CREATE TABLE asset_versions (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES assets(id),
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    original_filename VARCHAR(512) NOT NULL,
    mime_type VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0),
    checksum_sha256 VARCHAR(64) NOT NULL,
    storage_object_id UUID NOT NULL REFERENCES storage_objects(id),
    uploaded_by_user_id UUID NOT NULL,
    processing_status VARCHAR(32) NOT NULL CHECK (processing_status IN ('UPLOADED','QUEUED','PROCESSING','READY','PARTIALLY_PROCESSED','FAILED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL,
    CONSTRAINT uk_asset_version_number UNIQUE (asset_id, version_number)
);
CREATE INDEX idx_asset_versions_checksum ON asset_versions(checksum_sha256);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    user_id UUID NOT NULL,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    operation_type VARCHAR(64) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('IN_PROGRESS','COMPLETED','FAILED')),
    resource_id UUID,
    response_status INTEGER,
    response_body TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL,
    CONSTRAINT uk_idempotency_scope UNIQUE (user_id, workspace_id, operation_type, idempotency_key)
);
CREATE INDEX idx_idempotency_expiry ON idempotency_records(expires_at);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_version INTEGER NOT NULL,
    topic VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING','PROCESSING','PUBLISHED','FAILED')),
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL
);
CREATE INDEX idx_outbox_polling ON outbox_events(status, next_attempt_at, created_at);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    consumer_name VARCHAR(128) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
