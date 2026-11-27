package com.petroad.backend.repository;

import com.petroad.backend.domain.UploadedImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface UploadedImageRepository extends JpaRepository<UploadedImage, Long> {
    boolean existsByUserIdAndUrl(Long userId, String url);

    // 아래 두 조회의 '사용 중' 검사는 누구의 기록이든 확인한다. 예전 데이터에는 여러 사용자가 같은 사진을 쓰는 경우가 있다.
    String NOT_IN_USE = " and not exists (select 1 from Course c where c.coverImageUrl = i.url)"
            + " and not exists (select 1 from Dog d where d.profileImage = i.url)"
            + " and not exists (select 1 from WalkLog w where w.cover = i.url)"
            + " and not exists (select 1 from WalkLog w join w.photos p where p = i.url)";

    /** 주어진 주소 중 이 사용자가 올렸고 어떤 기록도 쓰지 않는 사진. */
    @Query("select i from UploadedImage i where i.userId = :userId and i.url in :urls" + NOT_IN_USE)
    List<UploadedImage> findUnused(@Param("userId") Long userId, @Param("urls") Collection<String> urls);

    /** 이 사용자가 올린 사진 중 어떤 기록도 쓰지 않는 사진. */
    @Query("select i from UploadedImage i where i.userId = :userId" + NOT_IN_USE)
    List<UploadedImage> findAllUnused(@Param("userId") Long userId);

    // 커밋이 끝난 뒤에 호출하므로 새 트랜잭션이 필요하다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query("delete from UploadedImage i where i.url = :url")
    void deleteByUrlInNewTransaction(@Param("url") String url);
}
