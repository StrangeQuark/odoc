CREATE TABLE media_upload_sessions (
    id UUID PRIMARY KEY,
    space_id UUID NOT NULL REFERENCES spaces (id) ON DELETE CASCADE,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    expected_size_bytes BIGINT NOT NULL CHECK (expected_size_bytes > 0),
    expected_sha256 VARCHAR(64) NOT NULL CHECK (expected_sha256 ~ '^[0-9a-f]{64}$'),
    state VARCHAR(24) NOT NULL CHECK (state IN ('PENDING', 'COMPLETED', 'EXPIRED')),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_media_id UUID REFERENCES media_assets (id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX media_upload_sessions_expiry_idx ON media_upload_sessions (state, expires_at);
