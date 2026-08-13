CREATE TABLE asset_semantic_indexes (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    asset_id UUID NOT NULL REFERENCES assets(id),
    asset_version_id UUID NOT NULL REFERENCES asset_versions(id),
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED', 'NOT_APPLICABLE', 'DISABLED')),
    embedding_model VARCHAR(128),
    embedding_dimension INTEGER,
    chunk_count INTEGER NOT NULL DEFAULT 0 CHECK (chunk_count >= 0),
    failure_code VARCHAR(64),
    failure_message VARCHAR(1000),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL,
    CONSTRAINT uk_asset_semantic_indexes_version UNIQUE (asset_version_id)
);

CREATE INDEX idx_asset_semantic_indexes_workspace_asset
    ON asset_semantic_indexes(workspace_id, asset_id, created_at DESC);

CREATE TABLE asset_content_chunks (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    asset_id UUID NOT NULL REFERENCES assets(id),
    asset_version_id UUID NOT NULL REFERENCES asset_versions(id),
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    content TEXT NOT NULL,
    character_count INTEGER NOT NULL CHECK (character_count > 0),
    content_hash VARCHAR(64) NOT NULL,
    embedding vector(1536),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL,
    CONSTRAINT uk_asset_content_chunks_version_index UNIQUE (asset_version_id, chunk_index)
);

CREATE INDEX idx_asset_content_chunks_workspace_asset
    ON asset_content_chunks(workspace_id, asset_id, asset_version_id, chunk_index);

CREATE INDEX idx_asset_content_chunks_embedding_hnsw
    ON asset_content_chunks USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64)
    WHERE embedding IS NOT NULL;
