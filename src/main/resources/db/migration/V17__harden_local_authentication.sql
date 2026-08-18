ALTER TABLE auth_sessions
    ADD COLUMN authenticated_at TIMESTAMPTZ;

UPDATE auth_sessions SET authenticated_at = created_at WHERE authenticated_at IS NULL;

ALTER TABLE auth_sessions ALTER COLUMN authenticated_at SET NOT NULL;

CREATE TABLE auth_rate_limit_buckets (
    bucket_key VARCHAR(128) PRIMARY KEY,
    window_started_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL CHECK (attempts >= 0),
    blocked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX auth_rate_limit_buckets_updated_at_idx ON auth_rate_limit_buckets (updated_at);

CREATE TABLE auth_security_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    user_id UUID,
    origin_lookup_token BYTEA,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX auth_security_events_user_occurred_at_idx
    ON auth_security_events (user_id, occurred_at DESC);
