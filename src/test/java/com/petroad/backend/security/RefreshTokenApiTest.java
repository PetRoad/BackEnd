package com.petroad.backend.security;

import com.fasterxml.jackson.databind.*;
import com.petroad.backend.api.AuthenticationFailedException;
import com.petroad.backend.domain.User;
import com.petroad.backend.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "PETROAD_DB_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "jwt.secret=feedback-api-test-only-key-do-not-deploy-0123456789",
        "spring.datasource.username=petroad_test", "spring.datasource.password=test-only-password",
        "spring.flyway.schemas=feedback_api_test", "spring.flyway.default-schema=feedback_api_test",
        "spring.jpa.properties.hibernate.default_schema=feedback_api_test"
})
@AutoConfigureMockMvc
class RefreshTokenApiTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PETROAD_DB_TEST_URL"));
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired RefreshTokenService refreshTokens;
    @MockBean Clock clock;
    private final Instant now = Instant.parse("2026-11-05T00:00:00Z");

    @BeforeEach void time() { when(clock.instant()).thenReturn(now); }

    private JsonNode login() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", "Password123!"))))
                .andExpect(status().isOk());
        return mapper.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", "Password123!"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessToken").isString()).andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshExpiresIn").value(2592000))
                .andReturn().getResponse().getContentAsString());
    }

    private String body(String raw) throws Exception { return mapper.writeValueAsString(Map.of("refreshToken", raw)); }

    @Test void rotationStoresOnlyHashesAndReplayRevokesTheWholeSessionPersistently() throws Exception {
        JsonNode first = login();
        String original = first.get("refreshToken").asText();
        Long userId = first.get("userId").asLong();
        assertThat(jwt.parseUserId(first.get("accessToken").asText())).isEqualTo(userId);
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + original))
                .andExpect(status().isUnauthorized());
        var hashes = jdbc.queryForList("select t.token_hash from feedback_api_test.refresh_token t "
                + "join feedback_api_test.refresh_session s on s.id=t.session_id where s.user_id=?", String.class, userId);
        assertThat(hashes).hasSize(1).allMatch(hash -> hash.matches("[0-9a-f]{64}") && !hash.equals(original));
        String refreshed = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(original)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode second = mapper.readTree(refreshed);
        assertThat(second.get("refreshToken").asText()).isNotEqualTo(original);
        assertThat(second.get("accessToken").asText()).isNotEqualTo(first.get("accessToken").asText());
        assertThat(second.get("userId").asLong()).isEqualTo(userId);
        // Each MVC request has its own transaction: replay revocation must survive the 401.
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(original)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(second.get("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select revoked from feedback_api_test.refresh_session where user_id=?", Boolean.class, userId)).isTrue();
    }

    @Test void logoutRevokesOnlyThatLoginAndIsIdempotent() throws Exception {
        User user = users.save(new User(UUID.randomUUID() + "@example.com", "fixture-hash"));
        var one = refreshTokens.issue(user);
        var two = refreshTokens.issue(user);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content(body(one.refreshToken())))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(one.refreshToken())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(two.refreshToken())))
                .andExpect(status().isOk());
    }

    @Test void refreshExpiresAtTheAbsoluteDeadlineAndDoesNotExtendIt() throws Exception {
        JsonNode first = login();
        when(clock.instant()).thenReturn(now.plus(Duration.ofDays(29)));
        JsonNode second = mapper.readTree(mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(body(first.get("refreshToken").asText())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refreshExpiresIn").value(86400))
                .andReturn().getResponse().getContentAsString());
        when(clock.instant()).thenReturn(now.plus(Duration.ofDays(30)));
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(second.get("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
    }

    @Test void malformedUnknownAndMissingTokensAreRejected() throws Exception {
        for (String token : List.of("wrong", "A".repeat(43))) {
            mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(token)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content(body("wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test void simultaneousReuseAllowsOneRotationThenRevokesTheSession() throws Exception {
        var pair = refreshTokens.issue(users.save(new User(UUID.randomUUID() + "@example.com", "fixture-hash")));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Object> call = () -> {
                start.await();
                try { return refreshTokens.refresh(pair.refreshToken()); }
                catch (AuthenticationFailedException e) { return e; }
            };
            Future<Object> a = executor.submit(call), b = executor.submit(call);
            start.countDown();
            List<Object> results = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertThat(results.stream().filter(RefreshTokenService.TokenPair.class::isInstance)).hasSize(1);
            assertThat(results.stream().filter(AuthenticationFailedException.class::isInstance)).hasSize(1);
            var success = (RefreshTokenService.TokenPair) results.stream().filter(RefreshTokenService.TokenPair.class::isInstance).findFirst().orElseThrow();
            assertThatThrownBy(() -> refreshTokens.refresh(success.refreshToken())).isInstanceOf(AuthenticationFailedException.class);
        } finally { executor.shutdownNow(); }
    }

    @Test void walkRegistrationReturnsDateAndDurationAndOnlyOwnerCanDelete() throws Exception {
        JsonNode owner = login(), other = login();
        String auth = "Bearer " + owner.get("accessToken").asText();
        String response = mvc.perform(post("/api/walk-logs").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distance\":2.4,\"durationSec\":1800,\"title\":\"저녁 산책\",\"photos\":[\"https://example.com/walk.jpg\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.durationSec").value(1800))
                .andExpect(jsonPath("$.date").isString()).andExpect(jsonPath("$.photos[0]").value("https://example.com/walk.jpg"))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(response).get("id").asLong();
        mvc.perform(get("/api/me/walks/" + id).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.durationSec").value(1800))
                .andExpect(jsonPath("$.distanceMeters").value(2400));
        mvc.perform(delete("/api/me/walks/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/me/walks/" + id).header("Authorization", "Bearer " + other.get("accessToken").asText()))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from feedback_api_test.walk_log where id=?", Integer.class, id)).isEqualTo(1);
        mvc.perform(delete("/api/me/walks/" + id).header("Authorization", auth)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/walks/" + id).header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/me/walks/" + id).header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/stats").header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.walkCount").value(0));
        assertThat(jdbc.queryForObject("select count(*) from feedback_api_test.walk_log_photos where walk_log_id=?", Integer.class, id)).isZero();
    }

    @Test void bothDogEndpointsRejectFutureBirthWithoutChangingStoredProfile() throws Exception {
        String auth = "Bearer " + login().get("accessToken").asText();
        String valid = "{\"name\":\"콩이\",\"breed\":\"말티즈\",\"size\":\"SMALL\",\"birthDate\":\"2024-01-01\"}";
        mvc.perform(put("/api/me/dog").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isOk());
        for (String endpoint : List.of("/api/me/dog", "/api/dog")) {
            mvc.perform(put(endpoint).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                            .content(valid.replace("2024-01-01", "2999-01-01")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("birthDate")));
        }
        mvc.perform(get("/api/dog").header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.birthDate").value("2024-01-01"));
        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
        mvc.perform(put("/api/dog").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(valid.replace("2024-01-01", today)))
                .andExpect(status().isOk());
    }

    @Test void legacyWalksKeepUnknownDurationAndExposeTheSameSeoulDateEverywhere() throws Exception {
        JsonNode owner = login();
        long userId = owner.get("userId").asLong();
        String auth = "Bearer " + owner.get("accessToken").asText();
        long id = jdbc.queryForObject("insert into feedback_api_test.walk_log(user_id,distance,walked_at) "
                + "values (?,1.2,timestamp '2026-11-04 16:00:00') returning id", Long.class, userId);
        mvc.perform(get("/api/walk-logs").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].date").value("2026-11-05"))
                .andExpect(jsonPath("$[0].distance").value(1.2))
                .andExpect(jsonPath("$[0].durationSec").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/me/walks/" + id).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.date").value("2026-11-05"))
                .andExpect(jsonPath("$.durationSec").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(post("/api/walk-logs").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distance\":1.2,\"durationSec\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/walk-logs").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distance\":1.2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.durationSec").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test void deletingWalkLeavesItsSharedCourseAndLikesIntact() throws Exception {
        JsonNode owner = login();
        long userId = owner.get("userId").asLong();
        String auth = "Bearer " + owner.get("accessToken").asText();
        long courseId = jdbc.queryForObject("insert into feedback_api_test.course(user_id,name,distance,start_lat,start_lng,like_count) "
                + "values (?,'공유 코스',1.2,37.58,127.01,1) returning id", Long.class, userId);
        jdbc.update("insert into feedback_api_test.course_likes(user_id,course_id) values (?,?)", userId, courseId);
        JsonNode walk = mapper.readTree(mvc.perform(post("/api/walk-logs").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distance\":1.2,\"courseId\":" + courseId + ",\"durationSec\":300}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(delete("/api/me/walks/" + walk.get("id").asLong()).header("Authorization", auth))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/stats").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.walkCount").value(0))
                .andExpect(jsonPath("$.totalDistanceMeters").value(0))
                .andExpect(jsonPath("$.courseCount").value(1)).andExpect(jsonPath("$.likedCourseCount").value(1));
    }

    @Test void historicalMissingTimestampDoesNotBreakWalkResponses() throws Exception {
        JsonNode owner = login();
        String auth = "Bearer " + owner.get("accessToken").asText();
        long id = jdbc.queryForObject("insert into feedback_api_test.walk_log(user_id,distance) values (?,1.2) returning id",
                Long.class, owner.get("userId").asLong());
        mvc.perform(get("/api/walk-logs").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].date").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/me/walks/" + id).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.date").value(org.hamcrest.Matchers.nullValue()));
    }
}
