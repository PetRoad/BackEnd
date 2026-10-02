-- Additive schema update for an existing PostgreSQL database.
-- Review and apply through the team's deployment/migration process.
BEGIN;
ALTER TABLE walk_log ADD COLUMN IF NOT EXISTS title varchar(255);
ALTER TABLE walk_log ADD COLUMN IF NOT EXISTS duration_sec integer;
CREATE TABLE IF NOT EXISTS walk_log_photos (
    walk_log_id bigint NOT NULL REFERENCES walk_log(id),
    sequence integer NOT NULL,
    photo_url varchar(2048) NOT NULL,
    PRIMARY KEY (walk_log_id, sequence)
);
COMMIT;
