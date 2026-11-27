package com.petroad.backend.api;

import com.petroad.backend.config.RequestBodyLimitFilter;
import com.petroad.backend.domain.User;
import com.petroad.backend.repository.UserRepository;
import com.petroad.backend.security.JwtService;
import com.petroad.backend.storage.ImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "PETROAD_DB_TEST_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "jwt.secret=body-limit-http-test-only-key-never-deploy-0123456789",
        "spring.datasource.username=petroad_test", "spring.datasource.password=test-only-password",
        "spring.flyway.schemas=body_limit_test", "spring.flyway.default-schema=body_limit_test",
        "spring.jpa.properties.hibernate.default_schema=body_limit_test",
        "spring.datasource.hikari.schema=body_limit_test"
})
class RequestBodyLimitHttpTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PETROAD_DB_TEST_URL"));
    }
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @MockBean ImageStorage images;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @ParameterizedTest @ValueSource(strings = {"content-length", "chunked", "trailing-whitespace"})
    void actualHttpRequestsOverTwoMegabytesReturn413(String mode) throws Exception {
        byte[] bytes = mode.equals("trailing-whitespace")
                ? ("{}" + " ".repeat(RequestBodyLimitFilter.MAX_BYTES)).getBytes(StandardCharsets.UTF_8)
                : ("{\"padding\":\"" + "x".repeat(RequestBodyLimitFilter.MAX_BYTES) + "\"}").getBytes(StandardCharsets.UTF_8);
        // ofInputStream has unknown length: Java's HTTP/1.1 client sends Transfer-Encoding: chunked.
        HttpRequest.BodyPublisher body = mode.equals("content-length") ? HttpRequest.BodyPublishers.ofByteArray(bytes)
                : HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes));
        HttpResponse<String> result = client.send(request("/api/auth/signup").header("Content-Type", "application/json")
                .POST(body).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).isEqualTo(413);
        assertThat(result.body()).contains("message", "2MB");
    }

    @Test void exactTwoMegabytesStillReachNormalRequestValidation() throws Exception {
        String body = "{}" + " ".repeat(RequestBodyLimitFilter.MAX_BYTES - 2);
        HttpResponse<String> result = client.send(request("/api/auth/signup").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).isEqualTo(400);
        assertThat(result.body()).contains("message", "email");
    }

    @Test void imageUploadKeepsItsExistingTenMegabyteLimitInsteadOfTheJsonLimit() throws Exception {
        User user = users.save(new User(UUID.randomUUID() + "@example.com", "fixture-hash"));
        String url = "http://localhost:8080/uploads/walk_photo/00000000-0000-0000-0000-000000000005.png";
        when(images.save(eq("walk_photo"), endsWith(".png"), any())).thenReturn(url);
        String boundary = "petroad-test-boundary";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"purpose\"\r\n\r\nWALK_PHOTO\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] photo = new byte[3 * 1024 * 1024];
        photo[0] = (byte) 0x89; photo[1] = 'P'; photo[2] = 'N'; photo[3] = 'G';
        bytes.write(photo);
        bytes.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> result = client.send(request("/api/uploads")
                .header("Authorization", "Bearer " + jwt.issue(user.getId()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).contains(url);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .version(HttpClient.Version.HTTP_1_1).timeout(Duration.ofSeconds(10));
    }
}
