package com.petroad.backend.api;

import com.petroad.backend.domain.*;
import com.petroad.backend.repository.*;
import com.petroad.backend.application.CourseRecommendationService;
import com.petroad.backend.application.CourseRecommendationService.CourseSummary;
import com.petroad.backend.application.CourseRecommendationService.Recommendations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Transactional
public class CourseController {
    private final CourseRepository courses;
    private final UserRepository users;
    private final CourseLikeRepository likes;
    private final CourseRecommendationService recommendations;

    public record Point(double lat, double lng) {
    }

    public record CourseCreateRequest(@NotBlank String name, String coverImageUrl, @Positive double distance, Difficulty difficulty,
                         DogSize dogSize, @DecimalMin("-90") double startLat, @DecimalMin("-180") double startLng,
                         @Size(max = 3) List<String> tags, @NotNull @Size(min = 2) List<Point> points) {
    }

    @Operation(summary = "산책 코스 등록")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public CourseSummary create(@RequestAttribute("userId") Long userId, @Valid @RequestBody CourseCreateRequest r) {
        User u = users.findById(userId).orElseThrow();
        Course c = new Course(u, r.name(), r.coverImageUrl(), r.distance(), r.difficulty(), r.dogSize(), r.startLat(), r.startLng());
        for (int i = 0; i < r.points().size(); i++) c.addPoint(r.points().get(i).lat(), r.points().get(i).lng(), i);
        if (r.tags() != null) r.tags().forEach(c::addTag);
        return to(courses.save(c));
    }

    @Operation(summary = "주변 추천 코스 조회", description = "1,500m 안에 3개 이상이면 반경 1,500m, 아니면 3,000m. 좋아요 내림차순·시작점 거리 오름차순으로 반환합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    @Transactional(readOnly = true)
    public Recommendations recommended(@RequestParam double lat, @RequestParam double lng) {
        return recommendations.recommended(lat, lng);
    }

    @Operation(summary = "코스 좋아요")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/like")
    public CourseSummary like(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        Course c = courses.findById(id).orElseThrow();
        if (!likes.existsByUserIdAndCourseId(userId, id)) {
            likes.save(new CourseLike(users.findById(userId).orElseThrow(), c));
            c.changeLikeCount(1);
            courses.save(c);
        }
        return to(c);
    }

    @Operation(summary = "코스 좋아요 취소")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}/like")
    public CourseSummary unlike(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        Course c = courses.findById(id).orElseThrow();
        if (likes.existsByUserIdAndCourseId(userId, id)) {
            likes.deleteByUserIdAndCourseId(userId, id);
            c.changeLikeCount(-1);
            courses.save(c);
        }
        return to(c);
    }

    private CourseSummary to(Course c) {
        return recommendations.summary(c);
    }
}
