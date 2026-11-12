ALTER TABLE walk_log
    ADD COLUMN cover VARCHAR(2048),
    ADD COLUMN completion INTEGER CHECK (completion BETWEEN 0 AND 100);

CREATE TABLE walk_log_route (
    walk_log_id BIGINT NOT NULL REFERENCES walk_log(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    lat DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lng DOUBLE PRECISION NOT NULL CHECK (lng BETWEEN -180 AND 180),
    PRIMARY KEY (walk_log_id, sequence)
);

ALTER TABLE walk_log
    DROP CONSTRAINT walk_log_course_id_fkey,
    ADD CONSTRAINT walk_log_course_id_fkey
        FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE SET NULL;

ALTER TABLE course_likes
    DROP CONSTRAINT course_likes_course_id_fkey,
    ADD CONSTRAINT course_likes_course_id_fkey
        FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE;

CREATE INDEX walk_log_course_idx ON walk_log(course_id);
CREATE INDEX course_likes_course_idx ON course_likes(course_id);
