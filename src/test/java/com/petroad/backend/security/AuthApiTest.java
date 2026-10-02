package com.petroad.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.petroad.backend.api.*;
import com.petroad.backend.config.PasswordConfig;
import com.petroad.backend.domain.User;
import com.petroad.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthApiTest {
    private static final String LEGACY_HASH = "$2a$10$abcdefghijklmnopqrstuuc6nhYTWdrlFaaMkzIm7puP.wHDe.5rq";
    private final ObjectMapper mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private UserRepository users;
    private DogRepository dogs;
    private MockMvc mvc;
    private MutableClock clock;
    private PasswordEncoder encoder;

    @BeforeEach void setup() {
        users = mock(UserRepository.class);
        dogs = mock(DogRepository.class);
        encoder = new PasswordConfig().passwordEncoder();
        clock = new MutableClock();
        var properties = LoginThrottleTest.settings(5, 30, 100);
        var controller = new AuthController(users, dogs, encoder, new JwtService(JwtServiceTest.KEY, 60000),
                new LoginThrottle(properties, clock), new ClientIpResolver(properties));
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });
    }

    private MockHttpServletRequestBuilder request(String path, String email, String password, String ip) throws Exception {
        Map<String, String> body = path.endsWith("/signup")
                ? Map.of("email", email, "password", password, "region", "서울")
                : Map.of("email", email, "password", password);
        return post(path).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))
                .with(servlet -> { servlet.setRemoteAddr(ip); return servlet; });
    }

    static Stream<String> overlongPasswords() { return Stream.of("a".repeat(73), "가".repeat(24) + "a", "😀".repeat(19)); }
    static Stream<String> boundaryPasswords() { return Stream.of("a".repeat(72), "가".repeat(24), "😀".repeat(18)); }

    @ParameterizedTest @MethodSource("overlongPasswords")
    void signupAndLoginRejectOverlongUtf8BeforeTouchingDatabase(String password) throws Exception {
        for (String path : new String[]{"/api/auth/signup", "/api/auth/login"}) {
            mvc.perform(request(path, "user@example.com", password, "192.0.2.1"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("72바이트")));
        }
        verifyNoInteractions(users, dogs);
    }

    @ParameterizedTest @MethodSource("boundaryPasswords")
    void exactly72BytesCanBeStoredAndAuthenticatedWithoutTruncation(String password) throws Exception {
        mvc.perform(request("/api/auth/signup", "user@example.com", password, "192.0.2.1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.access_token").isString());
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPassword()).isNotEqualTo(password);
        assertThat(encoder.matches(password, saved.getPassword())).isTrue();
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(saved));
        mvc.perform(request("/api/auth/login", "user@example.com", password, "192.0.2.1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user_id").value(42));
    }

    @Test void oldHashStillAuthenticatesAndLongerPasswordSharingItsPrefixDoesNot() throws Exception {
        User user = new User("legacy@example.com", LEGACY_HASH, "서울");
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 42L);
        when(users.findByEmail("legacy@example.com")).thenReturn(Optional.of(user));
        mvc.perform(request("/api/auth/login", "legacy@example.com", "legacy-password-2026", "192.0.2.1"))
                .andExpect(status().isOk());
        mvc.perform(request("/api/auth/login", "legacy@example.com", "wrong-password", "192.0.2.1"))
                .andExpect(status().isUnauthorized());
        String prefix = "a".repeat(72);
        String hash = encoder.encode(prefix);
        // CVE-2025-22228: the old encoder accepted suffixes after the first 72 bytes.
        assertThat(encoder.matches(prefix + "X", hash)).isFalse();
        assertThatThrownBy(() -> encoder.encode(prefix + "X")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void nullAndBlankSignupPasswordsReturn400() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"region\":\"서울\",\"password\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(request("/api/auth/signup", "user@example.com", "        ", "192.0.2.1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users);
    }

    @Test void accountLockCannotBeBypassedByChangingIpOrRepeatedRequestsAndExpires() throws Exception {
        for (int n = 0; n < 5; n++) {
            mvc.perform(request("/api/auth/login", "unknown@example.com", "wrong-password", "192.0.2." + n))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(request("/api/auth/login", "unknown@example.com", "wrong-password", "192.0.2.99"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.message").isString());
        verify(users, times(5)).findByEmail("unknown@example.com");
        clock.advance(Duration.ofMinutes(15));
        mvc.perform(request("/api/auth/login", "unknown@example.com", "wrong-password", "192.0.2.99"))
                .andExpect(status().isUnauthorized());
    }

    @Test void ipLimitAppliesAcrossAccountsAndIgnoresForgedForwardingHeaders() throws Exception {
        for (int n = 0; n < 30; n++) {
            mvc.perform(request("/api/auth/login", "unknown" + n + "@example.com", "wrong-password", "192.0.2.1")
                    .header("CF-Connecting-IP", "192.0.2." + (n + 10)).header("X-Forwarded-For", "192.0.2." + (n + 10)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(request("/api/auth/login", "new@example.com", "wrong-password", "192.0.2.1"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        clock.advance(Duration.ofMinutes(1));
        mvc.perform(request("/api/auth/login", "new@example.com", "wrong-password", "192.0.2.1"))
                .andExpect(status().isUnauthorized());
    }
}
