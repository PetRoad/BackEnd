package com.petroad.backend.api;

import com.petroad.backend.domain.*;
import com.petroad.backend.repository.*;
import com.petroad.backend.application.CourseRecommendationService;
import com.petroad.backend.application.MyPageService;
import com.petroad.backend.application.MyPageService.CourseDetail;
import com.petroad.backend.application.MyPageService.WalkRouteCoordinate;
import com.petroad.backend.application.UploadedImageService;
import com.petroad.backend.application.CourseRecommendationService.CourseSummary;
import com.petroad.backend.application.CourseRecommendationService.Recommendations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    private final MyPageService myPage;
    private final UploadedImageService uploadedImages;

    public record CourseCreateRequest(@NotBlank @Size(max = 30) String name,
                         @Size(max = 2048) String coverImageUrl,
                         @Positive @DecimalMax("200") double distance, Difficulty difficulty, DogSize dogSize,
                         @NotNull @DecimalMin("-90") @DecimalMax("90") Double startLat,
                         @NotNull @DecimalMin("-180") @DecimalMax("180") Double startLng,
                         @Size(max = 3) List<@NotBlank @Size(max = 20) String> tags,
                         @NotNull @Size(min = 2, max = 5000) List<@NotNull @Valid WalkRouteCoordinate> points) {
    }

    @Operation(summary = "산책 코스 등록", description = "coverImageUrl에는 본인이 업로드한 사진만 사용할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public CourseSummary create(@RequestAttribute("userId") Long userId, @Valid @RequestBody CourseCreateRequest r) {
        if (r.coverImageUrl() != null) uploadedImages.requireOwned(userId, r.coverImageUrl(), "coverImageUrl");
        User u = users.findById(userId).orElseThrow();
        Course c = new Course(u, r.name(), r.coverImageUrl(), r.distance(), r.difficulty(), r.dogSize(), r.startLat(), r.startLng());
        for (int i = 0; i < r.points().size(); i++) c.addPoint(r.points().get(i).lat(), r.points().get(i).lng(), i);
        if (r.tags() != null) r.tags().forEach(c::addTag);
        return recommendations.summary(courses.save(c), false);
    }

    @Operation(summary = "주변 추천 코스 조회", description = "1,500m 안에 3개 이상이면 반경 1,500m, 아니면 3,000m. 좋아요 내림차순·시작점 거리 오름차순으로 반환합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    @Transactional(readOnly = true)
    public Recommendations recommended(@RequestAttribute("userId") Long userId,
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lng) {
        return recommendations.recommended(userId, lat, lng);
    }

    @Operation(summary = "코스 상세 조회", description = "추천 반경과 관계없이 로그인 사용자가 코스 상세와 경로를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "코스 상세")
    @ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음")
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public CourseDetail detail(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        return myPage.course(userId, id);
    }

    @Operation(summary = "내가 만든 코스 삭제", description = "코스와 좋아요를 삭제합니다. 기존 산책일지와 완주율은 유지됩니다. 대표 사진 파일은 다른 기록이 쓰지 않으면 저장소에서도 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "삭제 완료")
    @ApiResponse(responseCode = "403", description = "코스 작성자만 삭제 가능")
    @ApiResponse(responseCode = "404", description = "코스를 찾을 수 없음")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        Course course = lockCourse(id);
        if (!course.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "내가 만든 코스만 삭제할 수 있습니다.");
        }
        String cover = course.getCoverImageUrl();
        courses.delete(course);
        if (cover != null) uploadedImages.releaseUnused(userId, List.of(cover));
    }

    @Operation(summary = "코스 좋아요")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/like")
    public CourseSummary like(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        Course c = lockCourse(id);
        if (!likes.existsByUserIdAndCourseId(userId, id)) {
            likes.save(new CourseLike(users.findById(userId).orElseThrow(), c));
            c.changeLikeCount(1);
            courses.save(c);
        }
        return recommendations.summary(c, true);
    }

    @Operation(summary = "코스 좋아요 취소")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}/like")
    public CourseSummary unlike(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        Course c = lockCourse(id);
        if (likes.existsByUserIdAndCourseId(userId, id)) {
            likes.deleteByUserIdAndCourseId(userId, id);
            c.changeLikeCount(-1);
            courses.save(c);
        }
        return recommendations.summary(c, false);
    }

    private Course lockCourse(Long id) {
        return courses.lockById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다."));
    }
}
