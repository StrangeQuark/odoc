CREATE TABLE workspace_invitations (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    email_lookup_token BYTEA NOT NULL,
    email_envelope TEXT NOT NULL,
    token_hash BYTEA NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uq_workspace_invitations_active_email
    ON workspace_invitations (workspace_id, email_lookup_token)
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

CREATE INDEX ix_workspace_invitations_token ON workspace_invitations (token_hash);
