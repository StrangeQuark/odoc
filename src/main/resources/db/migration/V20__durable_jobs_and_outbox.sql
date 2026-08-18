CREATE TABLE durable_jobs (
    id UUID PRIMARY KEY,
    workspace_id UUID REFERENCES workspaces (id) ON DELETE CASCADE,
    job_type VARCHAR(96) NOT NULL,
    schema_version INTEGER NOT NULL,
    payload_envelope TEXT NOT NULL,
    state VARCHAR(24) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    concurrency_key VARCHAR(160),
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    run_after TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_owner VARCHAR(120),
    lease_expires_at TIMESTAMP WITH TIME ZONE,
    lease_epoch BIGINT NOT NULL DEFAULT 0,
    last_error VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT durable_jobs_attempts_positive CHECK (attempt_count >= 0 AND max_attempts > 0)
);

CREATE INDEX durable_jobs_claim_idx ON durable_jobs (state, run_after, priority DESC, created_at);
CREATE INDEX durable_jobs_workspace_state_idx ON durable_jobs (workspace_id, state, run_after);
CREATE INDEX durable_jobs_lease_idx ON durable_jobs (lease_expires_at) WHERE state = 'RUNNING';

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    workspace_id UUID REFERENCES workspaces (id) ON DELETE CASCADE,
    aggregate_type VARCHAR(96) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    schema_version INTEGER NOT NULL,
    payload_envelope TEXT NOT NULL,
    state VARCHAR(24) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    run_after TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_owner VARCHAR(120),
    lease_expires_at TIMESTAMP WITH TIME ZONE,
    lease_epoch BIGINT NOT NULL DEFAULT 0,
    last_error VARCHAR(512),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX outbox_events_claim_idx ON outbox_events (state, run_after, occurred_at);
CREATE INDEX outbox_events_workspace_idx ON outbox_events (workspace_id, occurred_at DESC);
CREATE INDEX outbox_events_lease_idx ON outbox_events (lease_expires_at) WHERE state = 'PROCESSING';

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    workspace_id UUID REFERENCES workspaces (id) ON DELETE CASCADE,
    actor_user_id UUID,
    action VARCHAR(128) NOT NULL,
    target_type VARCHAR(96) NOT NULL,
    target_id UUID,
    outcome VARCHAR(24) NOT NULL,
    request_id VARCHAR(128),
    metadata_envelope TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source_outbox_id UUID NOT NULL UNIQUE REFERENCES outbox_events (id) ON DELETE RESTRICT
);

CREATE INDEX audit_events_workspace_time_idx ON audit_events (workspace_id, occurred_at DESC, id DESC);
CREATE INDEX audit_events_actor_time_idx ON audit_events (actor_user_id, occurred_at DESC, id DESC);
