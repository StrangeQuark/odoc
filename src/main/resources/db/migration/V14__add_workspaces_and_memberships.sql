CREATE TABLE workspaces (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE workspace_memberships (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    role VARCHAR(24) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT workspace_memberships_workspace_user_unique UNIQUE (workspace_id, user_id)
);

CREATE INDEX workspace_memberships_user_id_idx ON workspace_memberships (user_id, created_at);

ALTER TABLE spaces ADD COLUMN workspace_id UUID;

ALTER TABLE spaces DROP CONSTRAINT IF EXISTS spaces_space_key_key;

-- Existing pre-workspace development data remains intact but is deliberately not attached to a
-- newly enrolled account. It is isolated in this legacy scope rather than accidentally exposed.
INSERT INTO workspaces (id, name, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000014', 'Legacy development data', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

UPDATE spaces SET workspace_id = '00000000-0000-0000-0000-000000000014' WHERE workspace_id IS NULL;

ALTER TABLE spaces ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE spaces ADD CONSTRAINT spaces_workspace_id_fkey
    FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE;
CREATE INDEX spaces_workspace_id_name_idx ON spaces (workspace_id, name);
ALTER TABLE spaces ADD CONSTRAINT spaces_workspace_key_unique UNIQUE (workspace_id, space_key);
