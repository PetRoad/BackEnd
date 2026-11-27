package com.petroad.backend.api;

import com.petroad.backend.domain.User;
import com.petroad.backend.repository.UserRepository;
import com.petroad.backend.security.JwtService;
import com.petroad.backend.security.LoginRateLimitException;
import com.petroad.backend.storage.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "PETROAD_DB_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "jwt.secret=upload-limit-test-only-key-do-not-deploy-0123456789",
        "spring.datasource.username=petroad_test", "spring.datasource.password=test-only-password",
        "spring.flyway.schemas=upload_limit_test", "spring.flyway.default-schema=upload_limit_test",
        "spring.jpa.properties.hibernate.default_schema=upload_limit_test",
        "spring.datasource.hikari.schema=upload_limit_test"
})
@AutoConfigureMockMvc
class UploadRateLimitTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PETROAD_DB_TEST_URL"));
    }
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final String URL = "http://localhost:8080/uploads/walk_photo/00000000-0000-0000-0000-000000000006.png";

    @Autowired UploadController controller;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @MockBean ImageStorage images;
    @MockBean Clock clock;
    private final Instant now = Instant.parse("2026-11-27T00:00:00Z");

    @BeforeEach void reset() {
        when(clock.instant()).thenReturn(now);
        when(images.save(any(), any(), any())).thenReturn(URL);
        jdbc.update("delete from rate_limit_hits");
    }

    private static MockMultipartFile png() { return new MockMultipartFile("file", "a.png", "image/png", PNG); }

    private UploadController.UploadResponse upload(long userId) {
        return controller.upload(userId, png(), UploadController.Purpose.WALK_PHOTO);
    }

    private void seed(long userId, int count) {
        jdbc.update("insert into rate_limit_hits (bucket, key, hit_at) select 'upload', ?, ? from generate_series(1, ?)",
                String.valueOf(userId), OffsetDateTime.ofInstant(now, ZoneOffset.UTC), count);
    }

    private int recorded(long userId) {
        return jdbc.queryForObject("select count(*) from rate_limit_hits where bucket = 'upload' and key = ?",
                Integer.class, String.valueOf(userId));
    }

    @Test void the301stUploadIsBlockedUntilADayPasses() {
        seed(1L, 299);
        assertThat(upload(1L).url()).isEqualTo(URL);
        assertThatThrownBy(() -> upload(1L)).isInstanceOfSatisfying(LoginRateLimitException.class,
                exception -> assertThat(exception.getRetryAfterSeconds()).isEqualTo(86400));
        verify(images, times(1)).save(any(), any(), any());
        assertThat(upload(2L).url()).isEqualTo(URL);
        when(clock.instant()).thenReturn(now.plus(Duration.ofDays(1)));
        assertThat(upload(1L).url()).isEqualTo(URL);
    }

    @Test void onlySuccessfulSavesStayCounted() {
        seed(1L, 299);
        when(images.save(any(), any(), any())).thenThrow(new IllegalStateException("저장 실패")).thenReturn(URL);
        assertThatThrownBy(() -> upload(1L)).isInstanceOf(IllegalStateException.class);
        assertThat(recorded(1L)).isEqualTo(299);
        assertThat(upload(1L).url()).isEqualTo(URL);
        assertThat(recorded(1L)).isEqualTo(300);
    }

    @Test void rejectedFilesAreNotCounted() {
        var text = new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes());
        assertThatThrownBy(() -> controller.upload(1L, text, UploadController.Purpose.WALK_PHOTO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(recorded(1L)).isZero();
    }

    @Test void parallelUploadsRunTogetherWithoutExceedingTheLimit() throws Exception {
        seed(1L, 296);
        // 네 요청이 동시에 저장 단계에 들어와야 통과한다. 업로드가 한 장씩 처리되면 시간 초과로 실패한다.
        var savingTogether = new CyclicBarrier(4);
        when(images.save(any(), any(), any())).thenAnswer(invocation -> {
            savingTogether.await(10, TimeUnit.SECONDS);
            return URL;
        });
        int requests = 8;
        var pool = Executors.newFixedThreadPool(requests);
        try {
            var start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int n = 0; n < requests; n++) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        upload(1L);
                        return true;
                    } catch (LoginRateLimitException limited) {
                        return false;
                    }
                }));
            }
            start.countDown();
            List<Boolean> succeeded = new ArrayList<>();
            for (var result : results) succeeded.add(result.get(30, TimeUnit.SECONDS));
            assertThat(succeeded).filteredOn(ok -> ok).hasSize(4);
            assertThat(succeeded).filteredOn(ok -> !ok).hasSize(4);
            assertThat(recorded(1L)).isEqualTo(300);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void limitedUploadRequestReturns429WithRetryAfter() throws Exception {
        User user = users.save(new User(UUID.randomUUID() + "@example.com", "fixture-hash"));
        seed(user.getId(), 300);
        mvc.perform(multipart("/api/uploads").file(png()).param("purpose", "WALK_PHOTO")
                        .header("Authorization", "Bearer " + jwt.issue(user.getId())))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "86400"));
        verify(images, never()).save(any(), any(), any());
    }
}
