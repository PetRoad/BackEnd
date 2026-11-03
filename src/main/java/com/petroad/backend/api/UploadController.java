package com.petroad.backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    public enum Purpose { WALK_PHOTO, COURSE_COVER, DOG_PROFILE }

    public record UploadResponse(String url) {}

    @Operation(summary = "이미지 업로드")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam("purpose") Purpose purpose) {
        if (file.isEmpty()) throw new IllegalArgumentException("파일이 비어 있습니다.");

        String extension = detectExtension(file);
        if (extension == null) throw new IllegalArgumentException("jpg, png, webp 이미지만 올릴 수 있습니다.");

        String name = UUID.randomUUID() + "." + extension;
        return new UploadResponse("test://" + purpose.name().toLowerCase() + "/" + name);
    }

    // 파일 이름이 아니라 파일 맨 앞 몇 바이트(진짜 이미지인지 알려주는 표식)를 본다
    private static String detectExtension(MultipartFile file) {
        byte[] h;
        try (InputStream in = file.getInputStream()) {
            h = in.readNBytes(12);
        } catch (IOException e) {
            throw new IllegalArgumentException("파일을 읽을 수 없습니다.");
        }
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "jpg";
        if (h.length >= 4 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "png";
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "webp";
        return null;
    }
}