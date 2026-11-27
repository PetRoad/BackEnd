package com.petroad.backend.application;

import com.petroad.backend.domain.UploadedImage;
import com.petroad.backend.repository.UploadedImageRepository;
import com.petroad.backend.storage.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UploadedImageService {
    private final ImageStorage storage;
    private final UploadedImageRepository uploadedImages;
    private final ApplicationEventPublisher events;

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

    /**
     * 삭제하거나 교체한 기록의 사진을 정리한다. 다른 기록이 아직 쓰는 사진은 남긴다.
     * 기록을 삭제·수정한 트랜잭션 안에서, 변경한 뒤에 호출해야 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseUnused(Long userId, Collection<String> urls) {
        if (urls.isEmpty()) return;
        uploadedImages.flush();
        publish(uploadedImages.findUnused(userId, urls));
    }

    /** 탈퇴할 때, 저장하지 않은 것까지 포함해 올린 사진을 모두 정리한다. 내 기록을 삭제한 뒤에 호출해야 한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseAllUnused(Long userId) {
        uploadedImages.flush();
        publish(uploadedImages.findAllUnused(userId));
    }

    private void publish(List<UploadedImage> unused) {
        if (unused.isEmpty()) return;
        events.publishEvent(new ImagesReleased(unused.stream()
                .map(image -> new ImageStorage.StoredImage(image.getPublicId(), image.getUrl()))
                .toList()));
    }
}
