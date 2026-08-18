CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    email_lookup_token BYTEA NOT NULL UNIQUE,
    email_envelope TEXT NOT NULL,
    password_hash VARCHAR(512) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    token_hash BYTEA NOT NULL UNIQUE,
    csrf_token_hash BYTEA NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX auth_sessions_active_user_idx ON auth_sessions (user_id, expires_at) WHERE revoked_at IS NULL;
