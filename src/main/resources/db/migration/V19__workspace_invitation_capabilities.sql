CREATE TABLE workspace_invitation_capabilities (
    id UUID PRIMARY KEY,
    invitation_id UUID NOT NULL REFERENCES workspace_invitations (id) ON DELETE CASCADE,
    token_hash BYTEA NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX workspace_invitation_capabilities_invitation_expiry_idx
    ON workspace_invitation_capabilities (invitation_id, expires_at);
