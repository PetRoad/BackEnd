CREATE TABLE rate_limit_hits (
    id BIGSERIAL PRIMARY KEY,
    bucket VARCHAR(50) NOT NULL,
    key VARCHAR(255) NOT NULL,
    hit_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_rate_limit_hits_bucket_key_hit_at ON rate_limit_hits (bucket, key, hit_at);
