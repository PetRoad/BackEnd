package com.petroad.backend.application;

import com.petroad.backend.domain.UploadedImage;
import com.petroad.backend.repository.UploadedImageRepository;
import com.petroad.backend.storage.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UploadedImageService {
    private final ImageStorage storage;
    private final UploadedImageRepository uploadedImages;

    /** 저장에 성공한 사진을 올린 사람과 함께 기록한다. */
    public void record(Long userId, ImageStorage.StoredImage image) {
        uploadedImages.save(new UploadedImage(userId, image.publicId(), image.url()));
    }

    /** 현재 저장소가 발급한 주소이면서 이 사용자가 올린 사진만 통과시킨다. */
    public void requireOwned(Long userId, String url, String field) {
        if (url == null || !storage.isOwnUrl(url)) {
            throw new IllegalArgumentException(field + "는 이미지 업로드 API에서 발급한 주소여야 합니다.");
        }
        if (!uploadedImages.existsByUserIdAndUrl(userId, url)) {
            throw new IllegalArgumentException(field + "는 본인이 업로드한 사진이어야 합니다.");
        }
    }
}
