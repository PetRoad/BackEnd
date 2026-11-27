package com.petroad.backend.application;

import com.petroad.backend.storage.ImageStorage;
import java.util.List;

/**
 * 더 이상 쓰지 않아 저장소에서 지울 사진들. DB 변경이 커밋된 뒤에 처리한다.
 * 탈퇴하면 업로드 기록이 함께 지워지므로, 주소뿐 아니라 저장소 이름(publicId)까지 담는다.
 */
public record ImagesReleased(List<ImageStorage.StoredImage> images) {}
