package com.petroad.backend.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

/** 업로드 API로 저장한 사진. 올린 사람만 자기 기록에 사용할 수 있다. */
@Entity
@Table(name = "uploaded_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadedImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false, unique = true, length = 2048)
    private String publicId;
    @Column(nullable = false, unique = true, length = 2048)
    private String url;
    @Column(nullable = false)
    private Instant createdAt;

    public UploadedImage(Long userId, String publicId, String url) {
        this.userId = userId;
        this.publicId = publicId;
        this.url = url;
        this.createdAt = Instant.now();
    }
}
