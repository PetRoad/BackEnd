package com.petroad.backend.api;

import com.petroad.backend.domain.Course;
import com.petroad.backend.domain.WalkLog;
import com.petroad.backend.repository.CourseRepository;
import com.petroad.backend.repository.UserRepository;
import com.petroad.backend.repository.WalkLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/walk-logs")
@RequiredArgsConstructor
public class WalkLogController {
    private final WalkLogRepository logs;
    private final UserRepository users;
    private final CourseRepository courses;

    @Schema(name = "WalkLogCreateRequest")
    public record Create(
            Long courseId,
            @PositiveOrZero double distance,
            @Size(max = 100) String title,
            @Schema(description = "실제 산책 시간(초). 현재는 선택이며 미입력 시 null", example = "1800") @PositiveOrZero Integer durationSec,
            @Size(max = 100) List<@NotBlank @Size(max = 2048) @Pattern(regexp = "https?://.+") String> photos
    ) {
    }

    @Schema(name = "WalkLogResponse")
    public record Response(Long id, Long courseId,
                           @Schema(description = "실제 산책 거리(km)") double distance,
                           @Schema(description = "UTC 기준 저장 시각") LocalDateTime walkedAt,
                           @Schema(description = "서울 기준 산책 날짜") LocalDate date,
                           String title,
                           @Schema(description = "실제 산책 시간(초). 기존 기록·미입력은 null") Integer durationSec,
                           List<String> photos) {
    }

    @Operation(summary = "산책 기록 등록")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @Transactional
    public Response create(@RequestAttribute("userId") Long userId, @Valid @RequestBody Create r) {
        Course c = r.courseId() == null ? null : courses.findById(r.courseId()).orElseThrow();
        WalkLog log = new WalkLog(users.findById(userId).orElseThrow(), c, r.distance());
        log.setDetails(r.title(), r.durationSec(), r.photos());
        return to(logs.save(log));
    }

    @Operation(summary = "내 산책 기록 목록 조회")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    @Transactional(readOnly = true)
    public List<Response> list(@RequestAttribute("userId") Long userId) {
        return logs.findAllByUserIdOrderByWalkedAtDesc(userId).stream().map(this::to).toList();
    }

    private Response to(WalkLog w) {
        LocalDate date = w.getWalkedAt() == null ? null : w.getWalkedAt().atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate();
        return new Response(w.getId(), w.getCourse() == null ? null : w.getCourse().getId(), w.getDistance(),
                w.getWalkedAt(), date, w.getTitle(), w.getDurationSec(), List.copyOf(w.getPhotos()));
    }
}
