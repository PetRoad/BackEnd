package com.petroad.backend.repository;
import com.petroad.backend.domain.CourseLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Set;
public interface CourseLikeRepository extends JpaRepository<CourseLike,Long> {
 boolean existsByUserIdAndCourseId(Long u,Long c);
 void deleteByUserIdAndCourseId(Long u,Long c);
 List<CourseLike> findAllByUserIdOrderByIdDesc(Long userId);
 @Query("select l.course.id from CourseLike l where l.user.id = :userId and l.course.id in :courseIds")
 Set<Long> findLikedCourseIds(@Param("userId") Long userId, @Param("courseIds") Collection<Long> courseIds);
}
