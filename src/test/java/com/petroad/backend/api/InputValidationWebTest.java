package com.petroad.backend.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petroad.backend.application.CourseRecommendationService;
import com.petroad.backend.application.MyPageService;
import com.petroad.backend.application.UploadedImageService;
import com.petroad.backend.domain.Course;
import com.petroad.backend.domain.User;
import com.petroad.backend.repository.*;
import com.petroad.backend.storage.LocalImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InputValidationWebTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final UserRepository users = mock(UserRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final MyPageService myPage = mock(MyPageService.class);
    private final CourseRecommendationService recommendations = mock(CourseRecommendationService.class);
    private final UploadedImageRepository uploadRecords = mock(UploadedImageRepository.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new CourseController(courses, users, mock(CourseLikeRepository.class), recommendations,
                                myPage, new UploadedImageService(new LocalImageStorage("target/test-images", ""), uploadRecords,
                                        mock(org.springframework.context.ApplicationEventPublisher.class))),
                        new MyPageController(myPage), new UserController(users))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private static Map<String, Object> course() {
        return new LinkedHashMap<>(Map.of("name", "테스트 코스", "distance", 1.2,
                "startLat", 37.58, "startLng", 127.01,
                "points", List.of(Map.of("lat", 37.58, "lng", 127.01), Map.of("lat", 37.59, "lng", 127.02))));
    }

    static Stream<Arguments> invalidCourseFields() {
        return Stream.of(
                Arguments.of("startLat", null), Arguments.of("startLng", null),
                Arguments.of("startLat", -90.01), Arguments.of("startLat", 90.01),
                Arguments.of("startLng", -180.01), Arguments.of("startLng", 180.01),
                Arguments.of("name", "가".repeat(31)), Arguments.of("distance", 200.001),
                Arguments.of("tags", List.of("가".repeat(21))), Arguments.of("tags", List.of(" ")),
                Arguments.of("tags", Collections.singletonList(null)),
                Arguments.of("points", List.of(Map.of("lng", 0), Map.of("lat", 0, "lng", 0))),
                Arguments.of("points", List.of(Map.of("lat", 91, "lng", 0), Map.of("lat", 0, "lng", 0))),
                Arguments.of("points", List.of(Map.of("lat", 0, "lng", -181), Map.of("lat", 0, "lng", 0))),
                Arguments.of("points", Arrays.asList(null, Map.of("lat", 0, "lng", 0))),
                Arguments.of("points", Collections.nCopies(5001, Map.of("lat", 0, "lng", 0))));
    }

    @ParameterizedTest @MethodSource("invalidCourseFields")
    void invalidCoursesReturn400BeforeRepositories(String field, Object value) throws Exception {
        Map<String, Object> body = course();
        body.put(field, value);
        mvc.perform(post("/api/courses").requestAttr("userId", 7L).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString(field)));
        verifyNoInteractions(courses, users, recommendations);
    }

    @ParameterizedTest @ValueSource(strings = {"startLat", "startLng"})
    void omittedStartCoordinateIsNotSilentlyZero(String field) throws Exception {
        Map<String, Object> body = course();
        body.remove(field);
        mvc.perform(post("/api/courses").requestAttr("userId", 7L).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString(field)));
        verifyNoInteractions(courses, users);
    }

    @ParameterizedTest @ValueSource(strings = {"https://example.com/image.jpg",
            "https://res.cloudinary.com/test/image/fetch/https://example.com/image.jpg", "", "javascript:alert(1)"})
    void externalCourseCoversAreRejected(String url) throws Exception {
        Map<String, Object> body = course();
        body.put("coverImageUrl", url);
        mvc.perform(post("/api/courses").requestAttr("userId", 7L).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("coverImageUrl")));
        verifyNoInteractions(courses, users);
    }

    @Test void courseCoverUploadedBySomeoneElseIsRejected() throws Exception {
        Map<String, Object> body = course();
        body.put("coverImageUrl", "http://localhost:8080/uploads/course_cover/00000000-0000-0000-0000-000000000001.jpg");
        mvc.perform(post("/api/courses").requestAttr("userId", 7L).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("본인이 업로드한")));
        verifyNoInteractions(courses, users);
    }

    @Test void exactCourseLimitsAndCoordinateEdgesAreAcceptedInOrder() throws Exception {
        User owner = new User("test@example.com", "hash");
        ReflectionTestUtils.setField(owner, "id", 7L);
        when(users.findById(7L)).thenReturn(Optional.of(owner));
        when(courses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Map<String, Object> body = course();
        body.put("name", "가".repeat(30));
        body.put("distance", 200);
        body.put("startLat", -90);
        body.put("startLng", -180);
        body.put("tags", List.of("가".repeat(20)));
        List<Map<String, Integer>> points = new ArrayList<>(Collections.nCopies(5000, Map.of("lat", -90, "lng", -180)));
        points.set(4999, Map.of("lat", 90, "lng", 180));
        body.put("points", points);
        String cover = "http://localhost:8080/uploads/course_cover/00000000-0000-0000-0000-000000000001.jpg";
        when(uploadRecords.existsByUserIdAndUrl(7L, cover)).thenReturn(true);
        body.put("coverImageUrl", cover);
        mvc.perform(post("/api/courses").requestAttr("userId", 7L).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body))).andExpect(status().isOk());
        var saved = org.mockito.ArgumentCaptor.forClass(Course.class);
        verify(courses).save(saved.capture());
        assertThat(saved.getValue().getPoints()).hasSize(5000);
        assertThat(saved.getValue().getPoints().get(4999).getSequence()).isEqualTo(4999);
        assertThat(saved.getValue().getPoints().get(4999).getLat()).isEqualTo(90);
    }

    @ParameterizedTest @ValueSource(strings = {"91", "-91", "181", "NaN", "Infinity", "-Infinity"})
    void recommendationCoordinatesReturnTheMessageShape(String value) throws Exception {
        mvc.perform(get("/api/courses").requestAttr("userId", 7L).param("lat", value).param("lng", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("lat")));
        verifyNoInteractions(recommendations);
    }

    @Test void longitudeUpperBoundAndMissingCoordinatesAreRejected() throws Exception {
        mvc.perform(get("/api/courses").requestAttr("userId", 7L).param("lat", "0").param("lng", "181"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("lng")));
        mvc.perform(get("/api/courses").requestAttr("userId", 7L).param("lat", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("lng")));
        verifyNoInteractions(recommendations);
    }

    static Stream<Arguments> invalidWalkFields() {
        return Stream.of(Arguments.of("distanceMeters", 200000.01), Arguments.of("durationSec", 172801),
                Arguments.of("route", Collections.nCopies(20001, Map.of("lat", 0, "lng", 0))));
    }

    @ParameterizedTest @MethodSource("invalidWalkFields")
    void oversizedWalkValuesAreRejectedBeforeSaving(String field, Object value) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("distanceMeters", 0);
        body.put(field, value);
        mvc.perform(post("/api/me/walks").requestAttr("userId", 7L).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString(field)));
        verifyNoInteractions(myPage);
    }

    @Test void exactWalkLimitsAndEmptyRoutesAreAccepted() throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("distanceMeters", 200000, "durationSec", 172800,
                "route", Collections.nCopies(20000, Map.of("lat", 0, "lng", 0))));
        mvc.perform(post("/api/me/walks").requestAttr("userId", 7L).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body))).andExpect(status().isOk());
        body.put("route", List.of());
        mvc.perform(post("/api/me/walks").requestAttr("userId", 7L).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(body))).andExpect(status().isOk());
        verify(myPage, times(2)).saveWalk(eq(7L), isNull(), isNull(), eq(200000.0), eq(172800),
                isNull(), isNull(), isNull(), anyList());
    }

    @Test void regionLengthLimitPreservesTheUsedEndpoint() throws Exception {
        mvc.perform(put("/api/users/me/region").requestAttr("userId", 7L).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("region", "가".repeat(101)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("region")));
        verifyNoInteractions(users);
        when(users.findById(7L)).thenReturn(Optional.of(new User("test@example.com", "hash")));
        mvc.perform(put("/api/users/me/region").requestAttr("userId", 7L).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("region", "가".repeat(100)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.region").value("가".repeat(100)));
    }
}
