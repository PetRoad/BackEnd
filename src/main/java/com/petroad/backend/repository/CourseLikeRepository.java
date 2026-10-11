package com.petroad.backend.repository;
import com.petroad.backend.domain.CourseLike;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CourseLikeRepository extends JpaRepository<CourseLike,Long> {
 boolean existsByUserIdAndCourseId(Long u,Long c);
 void deleteByUserIdAndCourseId(Long u,Long c);
 List<CourseLike> findAllByUserIdOrderByIdDesc(Long userId);
}
