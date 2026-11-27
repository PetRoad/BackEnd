package com.petroad.backend.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorage {
    /** 저장한 사진. publicId는 저장소 안에서 사진을 가리키는 이름이고, url은 사진을 볼 수 있는 주소다. */
    record StoredImage(String publicId, String url) {}

    /** 사진을 저장하고, 저장한 사진의 정보를 돌려준다. */
    StoredImage save(String folder, String filename, MultipartFile file);

    /** 저장한 사진 파일을 지운다. 이미 없는 파일은 지운 것으로 본다. */
    void delete(String publicId);

    /** 현재 저장소의 업로드 API가 발급하는 이미지 주소인지 검사한다. 소유자 검사는 별도다. */
    boolean isOwnUrl(String url);
}
