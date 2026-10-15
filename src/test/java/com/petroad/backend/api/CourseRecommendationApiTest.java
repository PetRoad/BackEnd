package com.petroad.backend.api;

import com.petroad.backend.repository.UserRepository;
import com.petroad.backend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "PETROAD_DB_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "jwt.secret=course-api-test-only-key-do-not-deploy-0123456789",
        "spring.datasource.username=petroad_test", "spring.datasource.password=test-only-password",
        "spring.flyway.schemas=course_api_test", "spring.flyway.default-schema=course_api_test",
        "spring.jpa.properties.hibernate.default_schema=course_api_test"
})
@AutoConfigureMockMvc
@ActiveProfiles("local-mock")
class CourseRecommendationApiTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PETROAD_DB_TEST_URL"));
    }
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;

    @Test void actualPostgresFixturesReturnOnlyTwoNearbyCoursesWithCardFields() throws Exception {
        String token = jwt.issue(users.findByEmail("mock-river@petroad.local").orElseThrow().getId());
        String json = mvc.perform(get("/api/courses").param("lat","37.5836").param("lng","127.0100")
                        .header("Authorization","Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.radiusKm").value(3.0))
                .andExpect(jsonPath("$.radiusMeters").doesNotExist())
                .andExpect(jsonPath("$.courses",hasSize(2)))
                .andExpect(jsonPath("$.courses[0].name").value("성북천 따라 걷기"))
                .andExpect(jsonPath("$.courses[0].distanceKm").value(1.39))
                .andExpect(jsonPath("$.courses[0].distanceMeters").doesNotExist())
                .andExpect(jsonPath("$.courses[0].distanceFromMeters").doesNotExist())
                .andExpect(jsonPath("$.courses[0].distanceFromKm").value(closeTo(0.62339,0.00001), Double.class))
                .andExpect(jsonPath("$.courses[0].breed").value("포메라니안"))
                .andExpect(jsonPath("$.courses[0].dogAge").value(3))
                .andExpect(jsonPath("$.courses[0].tags",contains("물가","평지")))
                .andExpect(jsonPath("$.courses[0].dogSize").doesNotExist())
                .andExpect(jsonPath("$.courses[0].distance").doesNotExist())
                .andExpect(jsonPath("$.courses[1].name").value("낙산공원 성곽길"))
                .andExpect(jsonPath("$.courses[1].distanceKm").value(0.76))
                .andExpect(jsonPath("$.courses[1].distanceFromKm").value(closeTo(0.42615,0.00001), Double.class))
                .andExpect(jsonPath("$.courses[1].breed").value("말티즈"))
                .andExpect(jsonPath("$.courses[1].dogAge").value(2))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Files.writeString(Path.of("target/course-recommendations-verified.json"),json);
    }

    @Test void missingOrInvalidTokenReturns401() throws Exception {
        mvc.perform(get("/api/courses").param("lat","37.5836").param("lng","127.0100"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/courses").param("lat","37.5836").param("lng","127.0100")
                        .header("Authorization","Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test void swaggerCourseRequestHasDistinctSchemaAndRecommendationFields() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/courses'].post.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/CourseCreateRequest"))
                .andExpect(jsonPath("$.components.schemas.CourseCreateRequest.properties.points").exists())
                .andExpect(jsonPath("$.components.schemas.Recommendations.properties.radiusKm").exists())
                .andExpect(jsonPath("$.components.schemas.CourseSummary.properties.dogAge").exists())
                .andExpect(jsonPath("$.components.schemas.CourseSummary.properties.dogSize").doesNotExist());
    }
}
