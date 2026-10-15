package com.petroad.backend.api;

import com.petroad.backend.application.CourseRecommendationService;
import com.petroad.backend.domain.*;
import com.petroad.backend.repository.*;
import com.petroad.backend.security.JwtInterceptor;
import com.petroad.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CourseRecommendationWebTest {
    CourseRepository courses = mock(CourseRepository.class);
    DogRepository dogs = mock(DogRepository.class);
    JwtService jwt = new JwtService("local-web-test-only-key-never-deploy-0123456789", 60000);
    MockMvc mvc;

    @BeforeEach void setup() {
        var service = new CourseRecommendationService(courses, dogs);
        mvc = MockMvcBuilders.standaloneSetup(new CourseController(courses, mock(UserRepository.class),
                        mock(CourseLikeRepository.class), service))
                .setControllerAdvice(new ApiExceptionHandler()).addInterceptors(new JwtInterceptor(jwt)).build();
    }

    @Test void requestedMockCoursesProduceExpectedHttpResponse() throws Exception {
        User riverOwner = owner(1L), parkOwner = owner(2L), skyOwner = owner(3L);
        Course river = course(1L,riverOwner,"성북천 따라 걷기",1.39,Difficulty.EASY,75,37.5886,127.0068,"물가","평지");
        Course park = course(2L,parkOwner,"낙산공원 성곽길",0.76,Difficulty.NORMAL,42,37.5855,127.0058,"공원","야경");
        Course sky = course(3L,skyOwner,"북악 스카이웨이",1.52,Difficulty.HARD,210,37.6020,126.9810,"야경");
        when(courses.findAll()).thenReturn(List.of(river,park,sky));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        when(dogs.findAllByUserIdIn(Set.of(1L,2L))).thenReturn(List.of(
                new Dog(riverOwner,"콩이","포메라니안",DogSize.SMALL,today.minusYears(3),null),
                new Dog(parkOwner,"보리","말티즈",DogSize.SMALL,today.minusYears(2),null)));
        String json = mvc.perform(get("/api/courses").param("lat","37.5836").param("lng","127.0100")
                        .header("Authorization","Bearer " + jwt.issue(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.radiusKm").value(3.0))
                .andExpect(jsonPath("$.radiusMeters").doesNotExist())
                .andExpect(jsonPath("$.courses",hasSize(2)))
                .andExpect(jsonPath("$.courses[0].name").value("성북천 따라 걷기"))
                .andExpect(jsonPath("$.courses[0].distanceKm").value(1.39))
                .andExpect(jsonPath("$.courses[0].distanceMeters").doesNotExist())
                .andExpect(jsonPath("$.courses[0].distanceFromMeters").doesNotExist())
                .andExpect(jsonPath("$.courses[0].distanceFromKm").value(closeTo(0.62339,0.00001), Double.class))
                .andExpect(jsonPath("$.courses[0].tags",contains("물가","평지")))
                .andExpect(jsonPath("$.courses[0].breed").value("포메라니안"))
                .andExpect(jsonPath("$.courses[0].dogAge").value(3))
                .andExpect(jsonPath("$.courses[0].dogSize").doesNotExist())
                .andExpect(jsonPath("$.courses[0].distance").doesNotExist())
                .andExpect(jsonPath("$.courses[1].name").value("낙산공원 성곽길"))
                .andExpect(jsonPath("$.courses[1].distanceKm").value(0.76))
                .andExpect(jsonPath("$.courses[1].distanceFromKm").value(closeTo(0.42615,0.00001), Double.class))
                .andExpect(jsonPath("$.courses[1].breed").value("말티즈"))
                .andExpect(jsonPath("$.courses[1].dogAge").value(2))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Files.writeString(Path.of("target/course-recommendations-mock-verified.json"),json);
    }

    @Test void missingInvalidAndExpiredTokensReturn401WithoutQuerying() throws Exception {
        JwtService expired = new JwtService("local-web-test-only-key-never-deploy-0123456789",1);
        String oldToken = expired.issue(1L);
        Thread.sleep(5);
        mvc.perform(get("/api/courses").param("lat","0").param("lng","0"))
                .andExpect(status().isUnauthorized());
        for (String token : List.of("invalid",oldToken)) {
            mvc.perform(get("/api/courses").param("lat","0").param("lng","0")
                            .header("Authorization","Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(courses,dogs);
    }

    @Test void badCoordinatesReturn400AfterAuthentication() throws Exception {
        mvc.perform(get("/api/courses").param("lat","91").param("lng","127")
                        .header("Authorization","Bearer " + jwt.issue(1L)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(courses,dogs);
    }

    private User owner(Long id) {
        User user = new User("mock" + id + "@example.com","hash");
        ReflectionTestUtils.setField(user,"id",id);
        return user;
    }

    private Course course(Long id, User owner, String name, double km, Difficulty difficulty, int likes,
                          double lat, double lng, String... tags) {
        Course course = new Course(owner,name,null,km,difficulty,DogSize.SMALL,lat,lng);
        ReflectionTestUtils.setField(course,"id",id);
        course.changeLikeCount(likes);
        for (String tag : tags) course.addTag(tag);
        return course;
    }
}
