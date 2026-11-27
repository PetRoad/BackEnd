package com.petroad.backend.repository;

import com.petroad.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    // 이 잠금이 걸린 동안에는 이 사용자를 가리키는 새 행(업로드 기록 등)이 추가되지 못하고 기다린다.
    // JPA의 PESSIMISTIC_WRITE 대신 FOR UPDATE를 직접 쓴다.
    @Query(value = "SELECT id FROM {h-schema}users WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Long> lockForDeletion(@Param("id") Long id);
}
