package com.petroad.backend.api;

import com.petroad.backend.domain.Course;
import com.petroad.backend.domain.WalkLog;
import com.petroad.backend.repository.CourseRepository;
import com.petroad.backend.repository.UserRepository;
import com.petroad.backend.repository.WalkLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/walk-logs")
@RequiredArgsConstructor
public class WalkLogController {
    private final WalkLogRepository logs;
    private final UserRepository users;
    private final CourseRepository courses;

    public record Create(
            Long courseId,
            @PositiveOrZero double distance,
            @Size(max = 100) String title,
            @PositiveOrZero Integer durationSec,
            @Size(max = 100) List<@NotBlank @Size(max = 2048) @Pattern(regexp = "https?://.+") String> photos
    ) {
    }

    public record Response(Long id, Long courseId, double distance, LocalDateTime walkedAt) {
    }

    @Operation(summary = "산책 기록 등록")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public Response create(@RequestAttribute("userId") Long userId, @Valid @RequestBody Create r) {
        Course c = r.courseId() == null ? null : courses.findById(r.courseId()).orElseThrow();
        WalkLog log = new WalkLog(users.findById(userId).orElseThrow(), c, r.distance());
        log.setDetails(r.title(), r.durationSec(), r.photos());
        return to(logs.save(log));
    }

    @Operation(summary = "내 산책 기록 목록 조회")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public List<Response> list(@RequestAttribute("userId") Long userId) {
        return logs.findAllByUserIdOrderByWalkedAtDesc(userId).stream().map(this::to).toList();
    }

    private Response to(WalkLog w) {
        return new Response(w.getId(), w.getCourse() == null ? null : w.getCourse().getId(), w.getDistance(), w.getWalkedAt());
    }
}
