package com.petroad.backend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.petroad.backend.application.UploadedImageService;
import com.petroad.backend.security.RateLimiter;
import com.petroad.backend.storage.ImageStorage;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;



@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {
    private static final String UPLOAD_LIMIT_BUCKET = "upload";
    private static final int MAX_UPLOADS_PER_DAY = 300;
    private static final Duration UPLOAD_WINDOW = Duration.ofDays(1);

    private final ImageStorage storage;
    private final RateLimiter rateLimiter;
    private final UploadedImageService uploadedImages;
    public enum Purpose { WALK_PHOTO, COURSE_COVER, DOG_PROFILE }

    public record UploadResponse(String url) {}

    @Operation(summary = "이미지 업로드", description = "사용자당 최근 24시간에 300번까지 업로드할 수 있습니다. 저장에 실패한 업로드는 세지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업로드 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UploadResponse.class))),
            @ApiResponse(responseCode = "429", description = "업로드 횟수 제한. Retry-After 초 이후 다시 시도하세요.",
                    headers = @Header(name = "Retry-After", description = "재시도까지 대기할 초",
                            schema = @Schema(type = "integer", minimum = "1")),
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ApiError.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(@RequestAttribute("userId") Long userId,
                                 @RequestParam("file") MultipartFile file,
                                 @RequestParam("purpose") Purpose purpose) {
        if (file.isEmpty()) throw new IllegalArgumentException("파일이 비어 있습니다.");

        String extension = detectExtension(file);
        if (extension == null) throw new IllegalArgumentException("jpg, png, webp 이미지만 올릴 수 있습니다.");

        String name = UUID.randomUUID() + "." + extension;
        // 앱은 사진 여러 장을 동시에 올린다. 저장하는 동안 잠그지 않도록 횟수를 먼저 기록하고, 저장에 실패하면 취소한다.
        long hitId = rateLimiter.consume(UPLOAD_LIMIT_BUCKET, String.valueOf(userId),
                MAX_UPLOADS_PER_DAY, UPLOAD_WINDOW);
        // 기존: return new UploadResponse("test://" + ...);
        try {
            ImageStorage.StoredImage stored = storage.save(purpose.name().toLowerCase(), name, file);
            recordOrDiscard(userId, stored);
            return new UploadResponse(stored.url());
        } catch (RuntimeException failure) {
            cancelUploadRecord(hitId, failure);
            throw failure;
        }
    }

    // 기록하지 못한 사진은 아무도 쓸 수 없으므로 파일도 지운다.
    private void recordOrDiscard(Long userId, ImageStorage.StoredImage stored) {
        try {
            uploadedImages.record(userId, stored);
        } catch (RuntimeException failure) {
            try {
                storage.delete(stored.publicId());
            } catch (RuntimeException deleteFailure) {
                failure.addSuppressed(deleteFailure);
            }
            throw failure;
        }
    }

    // 취소까지 실패해도 원래 저장 오류가 가려지지 않게 한다.
    private void cancelUploadRecord(long hitId, RuntimeException saveFailure) {
        try {
            rateLimiter.cancel(hitId);
        } catch (RuntimeException cancelFailure) {
            saveFailure.addSuppressed(cancelFailure);
        }
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