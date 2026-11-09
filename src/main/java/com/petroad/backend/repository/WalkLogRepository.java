package com.petroad.backend.repository;
import com.petroad.backend.domain.WalkLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface WalkLogRepository extends JpaRepository<WalkLog,Long> {
 List<WalkLog> findAllByUserIdOrderByWalkedAtDesc(Long id);
 Optional<WalkLog> findByIdAndUserId(Long id, Long userId);
 @Modifying(flushAutomatically = true)
 @Query("update WalkLog w set w.course = null where w.course.id = :courseId")
 void clearCourse(@Param("courseId") Long courseId);
}
