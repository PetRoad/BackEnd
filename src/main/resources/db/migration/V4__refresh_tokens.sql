CREATE TABLE refresh_session (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_refresh_session_user ON refresh_session(user_id);
CREATE INDEX idx_refresh_session_expiry ON refresh_session(expires_at);

CREATE TABLE refresh_token (
    token_hash VARCHAR(64) PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES refresh_session(id) ON DELETE CASCADE,
    used BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_refresh_token_session ON refresh_token(session_id);
