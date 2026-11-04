package com.petroad.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalImageStorage implements ImageStorage {
    private final Path root;
    private final String baseUrl;

    public LocalImageStorage(@Value("${app.storage.local-dir:./uploads}") String dir,
                             @Value("${app.public-url:}") String publicUrl) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.baseUrl = publicUrl.isBlank() ? "http://localhost:8080" : publicUrl;
    }

    @Override
    public String save(String folder, String filename, MultipartFile file) {
        Path target = root.resolve(folder).resolve(filename).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("잘못된 저장 경로입니다.");
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("이미지 저장에 실패했습니다.", e);
        }
        return baseUrl + "/uploads/" + folder + "/" + filename;
    }
}