CREATE TABLE asset_text_contents (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    asset_id UUID NOT NULL REFERENCES assets(id),
    asset_version_id UUID NOT NULL REFERENCES asset_versions(id),
    extracted_text TEXT NOT NULL,
    character_count INTEGER NOT NULL CHECK (character_count >= 0),
    extraction_status VARCHAR(32) NOT NULL CHECK (extraction_status IN ('EXTRACTED','NO_TEXT_EXTRACTED','NOT_APPLICABLE','UNSUPPORTED')),
    extractor_type VARCHAR(32) NOT NULL,
    truncated BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL,
    CONSTRAINT uk_asset_text_contents_version UNIQUE (asset_version_id)
);
CREATE INDEX idx_asset_text_contents_workspace_asset ON asset_text_contents(workspace_id, asset_id);

CREATE TABLE asset_search_documents (
    asset_version_id UUID PRIMARY KEY REFERENCES asset_versions(id),
    asset_id UUID NOT NULL REFERENCES assets(id),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    display_name VARCHAR(255) NOT NULL,
    original_filename VARCHAR(512) NOT NULL,
    description VARCHAR(2000),
    mime_type VARCHAR(255) NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    extracted_text TEXT NOT NULL,
    search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(display_name, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(original_filename, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(description, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(extracted_text, '')), 'C')
    ) STORED,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_asset_search_documents_workspace ON asset_search_documents(workspace_id, asset_id);
CREATE INDEX idx_asset_search_documents_vector ON asset_search_documents USING GIN(search_vector);
