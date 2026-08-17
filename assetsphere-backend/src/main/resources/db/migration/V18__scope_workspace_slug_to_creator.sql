ALTER TABLE workspaces
    DROP CONSTRAINT IF EXISTS workspaces_slug_key;

ALTER TABLE workspaces
    ADD CONSTRAINT uk_workspaces_creator_slug UNIQUE (creator_user_id, slug);
