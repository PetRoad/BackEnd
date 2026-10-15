package com.petroad.backend.application;

import com.petroad.backend.domain.*;
import com.petroad.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseRecommendationServiceTest {
    @Mock CourseRepository courses;
    @Mock DogRepository dogs;
    @InjectMocks CourseRecommendationService service;

    @Test void haversineMatchesSeoulBusanAndHandlesCoincidentAndAntipodalPoints() {
        assertThat(CourseRecommendationService.distanceMeters(37.5665,126.9780,35.1796,129.0756))
                .isBetween(324000.0, 326000.0);
        assertThat(CourseRecommendationService.distanceMeters(0,0,0,0)).isZero();
        assertThat(CourseRecommendationService.distanceMeters(0,0,0,180)).isCloseTo(Math.PI * 6371000, within(0.01));
    }

    @Test void thirdNearbyCourseShrinksRadiusAndLikesTakePriorityOverDistance() {
        when(courses.findAll()).thenReturn(List.of(course(1L,0.002,10), course(2L,0.004,20),
                course(3L,0.006,10), course(4L,0.02,999)));
        when(dogs.findAllByUserIdIn(anyCollection())).thenReturn(List.of());
        var result = service.recommended(0,0);
        assertThat(result.radiusKm()).isEqualTo(1.5);
        assertThat(result.courses()).extracting(CourseRecommendationService.CourseSummary::id).containsExactly(2L,1L,3L);
        assertThat(result.courses().get(0).dogAge()).isNull();
    }

    @Test void twoNearbyCoursesExpandRadiusAndExcludeBeyondThreeKilometers() {
        when(courses.findAll()).thenReturn(List.of(course(1L,0.002,10), course(2L,0.004,20),
                course(3L,0.02,30), course(4L,0.04,999)));
        when(dogs.findAllByUserIdIn(anyCollection())).thenReturn(List.of());
        var result = service.recommended(0,0);
        assertThat(result.radiusKm()).isEqualTo(3.0);
        assertThat(result.courses()).extracting(CourseRecommendationService.CourseSummary::id).containsExactly(3L,2L,1L);
    }

    @Test void emptyRecommendationsStillReportExpandedRadius() {
        when(courses.findAll()).thenReturn(List.of());
        var result = service.recommended(0,0);
        assertThat(result.radiusKm()).isEqualTo(3.0);
        assertThat(result.courses()).isEmpty();
        verifyNoInteractions(dogs);
    }

    @Test void ageUsesCompletedYearsAndUnknownDatesStayUnknown() {
        LocalDate today = LocalDate.of(2026,10,15);
        assertThat(CourseRecommendationService.age(LocalDate.of(2023,10,15),today)).isEqualTo(3);
        assertThat(CourseRecommendationService.age(LocalDate.of(2023,10,16),today)).isEqualTo(2);
        assertThat(CourseRecommendationService.age(LocalDate.of(2026,1,1),today)).isZero();
        assertThat(CourseRecommendationService.age(null,today)).isNull();
        assertThat(CourseRecommendationService.age(today.plusDays(1),today)).isNull();
    }

    @Test void invalidCoordinatesAreRejectedBeforeQuerying() {
        assertThatThrownBy(() -> service.recommended(91,0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recommended(0,-181)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recommended(Double.NaN,0)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(courses, dogs);
    }

    private Course course(Long id, double latitude, int likes) {
        User owner = new User("owner" + id + "@example.com", "hash");
        org.springframework.test.util.ReflectionTestUtils.setField(owner, "id", id);
        Course course = new Course(owner,"코스",null,1.39,Difficulty.EASY,DogSize.SMALL,latitude,0);
        org.springframework.test.util.ReflectionTestUtils.setField(course, "id", id);
        course.changeLikeCount(likes);
        return course;
    }
}
