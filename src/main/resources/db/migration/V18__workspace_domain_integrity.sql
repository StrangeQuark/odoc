ALTER TABLE workspaces ADD COLUMN security_scope_id UUID;
UPDATE workspaces SET security_scope_id = id WHERE security_scope_id IS NULL;
ALTER TABLE workspaces ALTER COLUMN security_scope_id SET NOT NULL;
ALTER TABLE workspaces ADD CONSTRAINT workspaces_security_scope_unique UNIQUE (security_scope_id);
ALTER TABLE workspaces ADD COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE workspace_memberships ADD COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE';
CREATE INDEX workspace_memberships_workspace_status_idx
    ON workspace_memberships (workspace_id, status, created_at);

ALTER TABLE workspace_invitations ADD COLUMN route_id UUID;
UPDATE workspace_invitations SET route_id = id WHERE route_id IS NULL;
ALTER TABLE workspace_invitations ALTER COLUMN route_id SET NOT NULL;
ALTER TABLE workspace_invitations ADD CONSTRAINT workspace_invitations_route_id_unique UNIQUE (route_id);

CREATE TABLE workspace_groups (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name_lookup_token BYTEA NOT NULL,
    name_envelope TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT workspace_groups_workspace_name_unique UNIQUE (workspace_id, name_lookup_token),
    CONSTRAINT workspace_groups_id_workspace_unique UNIQUE (id, workspace_id)
);

CREATE TABLE workspace_group_members (
    group_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT workspace_group_members_group_workspace_fkey
        FOREIGN KEY (group_id, workspace_id) REFERENCES workspace_groups (id, workspace_id) ON DELETE CASCADE,
    CONSTRAINT workspace_group_members_workspace_membership_fkey
        FOREIGN KEY (workspace_id, user_id) REFERENCES workspace_memberships (workspace_id, user_id) ON DELETE CASCADE
);

CREATE INDEX workspace_group_members_workspace_user_idx
    ON workspace_group_members (workspace_id, user_id);
