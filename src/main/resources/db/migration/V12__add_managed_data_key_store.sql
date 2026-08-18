CREATE TABLE managed_data_keys (
    id UUID PRIMARY KEY,
    scope_kind VARCHAR(32) NOT NULL,
    scope_id UUID NOT NULL,
    purpose VARCHAR(64) NOT NULL,
    key_version INTEGER NOT NULL CHECK (key_version > 0),
    wrapping_key_version INTEGER NOT NULL CHECK (wrapping_key_version > 0),
    wrapped_dek BYTEA NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'RETIRED', 'DISABLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT managed_data_keys_scope_purpose_version_unique
        UNIQUE (scope_kind, scope_id, purpose, key_version)
);

CREATE UNIQUE INDEX managed_data_keys_single_active_scope_purpose
    ON managed_data_keys (scope_kind, scope_id, purpose)
    WHERE status = 'ACTIVE';
