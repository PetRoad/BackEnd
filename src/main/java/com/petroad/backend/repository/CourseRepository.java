package com.petroad.backend.repository; import com.petroad.backend.domain.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface CourseRepository extends JpaRepository<Course,Long>{
 @Query(value="SELECT c.* FROM course c WHERE (6371 * 2 * ASIN(SQRT(POWER(SIN(RADIANS(c.start_lat - :lat) / 2), 2) + COS(RADIANS(:lat)) * COS(RADIANS(c.start_lat)) * POWER(SIN(RADIANS(c.start_lng - :lng) / 2), 2)))) <= 3 ORDER BY c.like_count DESC, (6371 * 2 * ASIN(SQRT(POWER(SIN(RADIANS(c.start_lat - :lat) / 2), 2) + COS(RADIANS(:lat)) * COS(RADIANS(c.start_lat)) * POWER(SIN(RADIANS(c.start_lng - :lng) / 2), 2)))) ASC",nativeQuery=true)
 List<Course> findRecommended(@Param("lat") double lat,@Param("lng") double lng);
}
