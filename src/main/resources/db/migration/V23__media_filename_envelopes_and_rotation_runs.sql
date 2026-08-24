ALTER TABLE media_assets ADD COLUMN filename_envelope TEXT;
ALTER TABLE media_assets ALTER COLUMN filename DROP NOT NULL;

CREATE TABLE encryption_rotation_runs (
    id UUID PRIMARY KEY,
    scope_kind VARCHAR(32) NOT NULL,
    scope_id UUID NOT NULL,
    purpose VARCHAR(64) NOT NULL,
    from_key_version INTEGER NOT NULL,
    to_key_version INTEGER NOT NULL,
    state VARCHAR(24) NOT NULL CHECK (state IN ('PLANNED', 'RUNNING', 'COMPLETED', 'FAILED')),
    lease_owner VARCHAR(120),
    lease_epoch BIGINT NOT NULL DEFAULT 0,
    lease_expires_at TIMESTAMP WITH TIME ZONE,
    cursor_value VARCHAR(256),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (scope_kind, scope_id, purpose, to_key_version)
);

CREATE INDEX encryption_rotation_runs_claim_idx
    ON encryption_rotation_runs (state, lease_expires_at, created_at);
