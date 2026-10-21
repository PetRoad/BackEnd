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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

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
        "spring.jpa.properties.hibernate.default_schema=course_api_test",
        "spring.jpa.properties.hibernate.generate_statistics=true"
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
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    @Transactional
    void outsideCoursesAreNotLoadedAndTenCardsUseThreeQueries() throws Exception {
        verifyRecommendationQueryCost(10,100);
    }

    @Test
    @Transactional
    void tagQueryCountDoesNotGrowWithCardCount() throws Exception {
        verifyRecommendationQueryCost(3,0);
    }

    private void verifyRecommendationQueryCost(int nearbyCount, int distantCount) throws Exception {
        Long ownerId = jdbc.queryForObject("INSERT INTO course_api_test.users(email,password) VALUES (?,?) RETURNING id",
                Long.class,"query-cost-" + nearbyCount + "@petroad.local","fixture-hash");
        jdbc.update("INSERT INTO course_api_test.dog(user_id,name,breed,size,birth_date) VALUES (?,?,?,'SMALL',DATE '2023-01-01')",
                ownerId,"콩이","포메라니안");
        for (int i = 0; i < nearbyCount + distantCount; i++) {
            double lat = i < nearbyCount ? 0.002 + i * 0.0001 : 1.0;
            Long id = jdbc.queryForObject("INSERT INTO course_api_test.course "
                            + "(user_id,name,distance,start_lat,start_lng,difficulty,dog_size,like_count) "
                            + "VALUES (?,?,1.39,?,0,'EASY','SMALL',75) RETURNING id", Long.class,
                    ownerId,"query-cost-" + i,lat);
            jdbc.update("INSERT INTO course_api_test.course_tag(course_id,tag) VALUES (?,?),(?,?)",id,"물가",id,"평지");
        }
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mvc.perform(get("/api/courses").param("lat","0").param("lng","0")
                        .header("Authorization","Bearer " + jwt.issue(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.radiusKm").value(1.5))
                .andExpect(jsonPath("$.courses",hasSize(nearbyCount)))
                .andExpect(jsonPath("$.courses[0].tags",contains("물가","평지")))
                .andExpect(jsonPath("$.courses[0].breed").value("포메라니안"));

        org.assertj.core.api.Assertions.assertThat(statistics.getEntityStatistics(
                "com.petroad.backend.domain.Course").getLoadCount()).isEqualTo(nearbyCount);
        org.assertj.core.api.Assertions.assertThat(statistics.getEntityStatistics(
                "com.petroad.backend.domain.CourseTag").getLoadCount()).isZero();
        org.assertj.core.api.Assertions.assertThat(statistics.getCollectionFetchCount()).isZero();
        // One query each for nearby courses, owner dog profiles, and all selected tags.
        org.assertj.core.api.Assertions.assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        Files.writeString(Path.of("target/course-query-cost-" + nearbyCount + ".txt"),
                "nearby=" + nearbyCount + ", outside=" + distantCount + ", loadedCourses="
                        + statistics.getEntityStatistics("com.petroad.backend.domain.Course").getLoadCount()
                        + ", statements=" + statistics.getPrepareStatementCount()
                        + ", collectionFetches=" + statistics.getCollectionFetchCount());
    }

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
