package com.petroad.backend.application;

import com.petroad.backend.domain.*;
import com.petroad.backend.repository.CourseRepository;
import com.petroad.backend.repository.DogRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseRecommendationService {
    private final CourseRepository courses;
    private final DogRepository dogs;

    public record CourseSummary(Long id, String name, String coverImageUrl,
                                @Schema(description = "산책 코스 길이(km)") double distanceKm,
                                Difficulty difficulty, int likeCount, double startLat, double startLng,
                                @Schema(description = "요청 위치에서 코스 시작점까지 직선 거리(km). 등록·좋아요 응답에서는 null") Double distanceFromKm,
                                List<String> tags, String breed,
                                @Schema(description = "등록자 반려견의 현재 만 나이(서울 날짜 기준). 생년월일이 없으면 null") Integer dogAge) {}

    public record Recommendations(@Schema(description = "실제로 적용한 추천 반경(km): 1.5 또는 3.0")
                                  double radiusKm, List<CourseSummary> courses) {}

    public Recommendations recommended(double lat, double lng) {
        if (!Double.isFinite(lat) || lat < -90 || lat > 90
                || !Double.isFinite(lng) || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("위도는 -90~90, 경도는 -180~180 범위여야 합니다.");
        }
        List<LocatedCourse> located = courses.findAll().stream()
                .map(course -> new LocatedCourse(course,
                        distanceMeters(lat, lng, course.getStartLat(), course.getStartLng())))
                .toList();
        int radius = located.stream().filter(item -> item.meters() <= 1500).count() >= 3 ? 1500 : 3000;
        List<LocatedCourse> selected = located.stream().filter(item -> item.meters() <= radius)
                .sorted(Comparator.<LocatedCourse>comparingInt(item -> item.course().getLikeCount()).reversed()
                        .thenComparingDouble(LocatedCourse::meters).thenComparing(item -> item.course().getId()))
                .toList();
        Set<Long> ownerIds = selected.stream().map(item -> item.course().getUser().getId()).collect(Collectors.toSet());
        Map<Long, Dog> profiles = ownerIds.isEmpty() ? Map.of() : dogs.findAllByUserIdIn(ownerIds).stream()
                .collect(Collectors.toMap(dog -> dog.getUser().getId(), Function.identity()));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        return new Recommendations(radius / 1000.0, selected.stream()
                .map(item -> summary(item.course(), item.meters(), profiles.get(item.course().getUser().getId()), today))
                .toList());
    }

    public CourseSummary summary(Course course) {
        return summary(course, null, dogs.findByUserId(course.getUser().getId()).orElse(null),
                LocalDate.now(ZoneId.of("Asia/Seoul")));
    }

    private CourseSummary summary(Course course, Double meters, Dog dog, LocalDate today) {
        return new CourseSummary(course.getId(), course.getName(), course.getCoverImageUrl(),
                course.getDistance(), course.getDifficulty(), course.getLikeCount(),
                course.getStartLat(), course.getStartLng(), meters == null ? null : meters / 1000.0,
                course.getTags().stream().map(CourseTag::getTag).toList(),
                dog == null ? null : dog.getBreed(), dog == null ? null : age(dog.getBirthDate(), today));
    }

    static Integer age(LocalDate birthDate, LocalDate today) {
        return birthDate == null || birthDate.isAfter(today) ? null : Period.between(birthDate, today).getYears();
    }

    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * 6371000 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, h))));
    }

    private record LocatedCourse(Course course, double meters) {}
}
