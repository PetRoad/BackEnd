package com.petroad.backend.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorage {
    /** 사진을 저장하고, 그 사진을 볼 수 있는 주소(URL)를 돌려준다. */
    String save(String folder, String filename, MultipartFile file);

    /** 현재 저장소의 업로드 API가 발급하는 이미지 주소인지 검사한다. 소유자 검사는 별도다. */
    boolean isOwnUrl(String url);
}
