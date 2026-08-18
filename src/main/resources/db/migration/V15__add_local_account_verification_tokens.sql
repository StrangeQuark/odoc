ALTER TABLE user_accounts
    ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE;

-- Existing local-development accounts predate verifiable enrollment. Preserve their access while
-- every account created after this migration must complete the one-time verification flow.
UPDATE user_accounts
    SET email_verified_at = created_at
    WHERE email_verified_at IS NULL;

CREATE TABLE auth_action_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    action_type VARCHAR(32) NOT NULL,
    token_hash BYTEA NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT auth_action_tokens_type_check
        CHECK (action_type IN ('EMAIL_VERIFICATION', 'PASSWORD_RECOVERY'))
);

CREATE INDEX auth_action_tokens_active_user_idx
    ON auth_action_tokens (user_id, action_type, expires_at)
    WHERE consumed_at IS NULL;
