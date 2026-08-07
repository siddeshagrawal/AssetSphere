ALTER TABLE audit_records
    ALTER COLUMN metadata TYPE TEXT
    USING metadata::TEXT;
