ALTER TABLE walk_log
    ADD COLUMN title VARCHAR(255),
    ADD COLUMN duration_sec INTEGER;

CREATE TABLE walk_log_photos (
    walk_log_id BIGINT NOT NULL REFERENCES walk_log(id),
    sequence INTEGER NOT NULL,
    photo_url VARCHAR(2048) NOT NULL,
    PRIMARY KEY (walk_log_id, sequence)
);