package com.petroad.backend.application;

import com.petroad.backend.repository.UploadedImageRepository;
import com.petroad.backend.storage.ImageStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReleasedImageCleaner {
    private final ImageStorage storage;
    private final UploadedImageRepository uploadedImages;

    // DB가 롤백되면 호출되지 않는다. 파일은 되살릴 수 없으므로 커밋이 끝난 뒤에만 지운다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void delete(ImagesReleased event) {
        for (ImageStorage.StoredImage image : event.images()) {
            try {
                storage.delete(image.publicId());
                uploadedImages.deleteByUrlInNewTransaction(image.url());
            } catch (RuntimeException failure) {
                // 사용자의 삭제 요청은 이미 끝났다. 실패한 사진은 로그를 남기고 나머지를 계속 지운다.
                log.warn("저장소의 사진을 정리하지 못했습니다. publicId={}", image.publicId(), failure);
            }
        }
    }
}
