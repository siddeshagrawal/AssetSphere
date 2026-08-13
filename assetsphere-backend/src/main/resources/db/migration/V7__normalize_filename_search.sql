DROP INDEX idx_asset_search_documents_vector;

ALTER TABLE asset_search_documents DROP COLUMN search_vector;

ALTER TABLE asset_search_documents
    ADD COLUMN search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(display_name, '')), 'A') ||
        setweight(to_tsvector('simple', regexp_replace(coalesce(original_filename, ''), '[._-]+', ' ', 'g')), 'A') ||
        setweight(to_tsvector('simple', coalesce(description, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(extracted_text, '')), 'C')
    ) STORED;

CREATE INDEX idx_asset_search_documents_vector ON asset_search_documents USING GIN(search_vector);
