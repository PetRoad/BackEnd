package com.petroad.backend.repository;

import com.petroad.backend.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findAllByUserIdOrderByIdDesc(Long userId);

    @Query(value = """
            SELECT c.* FROM {h-schema}course c
            WHERE 2 * 6371000 * ASIN(SQRT(LEAST(1.0, GREATEST(0.0,
                POWER(SIN(RADIANS(c.start_lat - :lat) / 2), 2)
                + COS(RADIANS(:lat)) * COS(RADIANS(c.start_lat))
                * POWER(SIN(RADIANS(c.start_lng - :lng) / 2), 2)
            )))) <= 3000
            """, nativeQuery = true)
    List<Course> findWithinThreeKilometers(@Param("lat") double lat, @Param("lng") double lng);

    interface TagValue {
        Long getCourseId();
        String getTag();
    }

    @Query("SELECT t.course.id AS courseId, t.tag AS tag FROM CourseTag t "
            + "WHERE t.course.id IN :courseIds ORDER BY t.id")
    List<TagValue> findTagValuesByCourseIdIn(@Param("courseIds") Collection<Long> courseIds);
}
