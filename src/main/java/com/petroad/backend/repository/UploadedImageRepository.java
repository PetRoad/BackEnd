package com.petroad.backend.repository;

import com.petroad.backend.domain.UploadedImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedImageRepository extends JpaRepository<UploadedImage, Long> {
    boolean existsByUserIdAndUrl(Long userId, String url);
}
