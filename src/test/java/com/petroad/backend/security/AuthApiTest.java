package com.petroad.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthApiTest {
    private static final String LEGACY_HASH = "$2a$10$abcdefghijklmnopqrstuuc6nhYTWdrlFaaMkzIm7puP.wHDe.5rq";
    private final ObjectMapper mapper = new ObjectMapper().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private UserRepository users;
    private MockMvc mvc;
    private MutableClock clock;
    private PasswordEncoder encoder;
    private RateLimiter rateLimiter;
    private LoginThrottle throttle;
    private LoginThrottle.Attempt attempt;

    @BeforeEach void setup() {
        users = mock(UserRepository.class);
        encoder = new PasswordConfig().passwordEncoder();
        clock = new MutableClock();
        rateLimiter = mock(RateLimiter.class);
        throttle = mock(LoginThrottle.class);
        attempt = mock(LoginThrottle.Attempt.class);
        when(throttle.begin(anyString(), anyString())).thenReturn(attempt);
        var properties = LoginThrottleTest.settings(5, 30, 100);
        var jwt = new JwtService(JwtServiceTest.KEY, 60000);
        var sessions = mock(RefreshSessionRepository.class);
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var refreshTokens = new RefreshTokenService(sessions, mock(RefreshTokenRepository.class), jwt, clock, Duration.ofDays(30));
        var controller = new AuthController(users, encoder,
                throttle, new ClientIpResolver(properties), refreshTokens, rateLimiter);
        mvc = MockMvcBuilders.standaloneSetup(controller, new UserController(users))
                .addMappedInterceptors(new String[]{"/api/users/**"}, new JwtInterceptor(jwt)).setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });
    }

    private MockHttpServletRequestBuilder request(String path, String email, String password, String ip) throws Exception {
        Map<String, String> body = Map.of("email", email, "password", password);
        return post(path).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))
                .with(servlet -> { servlet.setRemoteAddr(ip); return servlet; });
    }

    static Stream<String> overlongPasswords() { return Stream.of("a".repeat(73), "가".repeat(24) + "a", "😀".repeat(19)); }
    static Stream<String> boundaryPasswords() { return Stream.of("a".repeat(72), "가".repeat(24), "😀".repeat(18)); }

    @Test void overlongEmailsAreRejectedBeforeDatabaseAccess() throws Exception {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(63) + ".com";
        for (String path : new String[]{"/api/auth/signup", "/api/auth/login"}) {
            mvc.perform(request(path, email, "Password123!", "192.0.2.1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("email")));
        }
        verifyNoInteractions(users);
    }

    @ParameterizedTest @MethodSource("overlongPasswords")
    void signupAndLoginRejectOverlongUtf8BeforeTouchingDatabase(String password) throws Exception {
        for (String path : new String[]{"/api/auth/signup", "/api/auth/login"}) {
            mvc.perform(request(path, "user@example.com", password, "192.0.2.1"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString());
        }
        verifyNoInteractions(users);
    }

    @ParameterizedTest @MethodSource("boundaryPasswords")
    void existing72BytePasswordsStillAuthenticateWithoutTruncation(String password) throws Exception {
        User user = new User("user@example.com", encoder.encode(password));
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 42L);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        mvc.perform(request("/api/auth/login", "user@example.com", password, "192.0.2.1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.userId").value(42)).andExpect(jsonPath("$.access_token").doesNotExist());
    }

    @Test void signupThenLoginAndRegionSetupUseTheNewContract() throws Exception {
        String password = "Password123!";
        mvc.perform(request("/api/auth/signup", "user@example.com", password, "192.0.2.1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.accessToken").doesNotExist()).andExpect(jsonPath("$.access_token").doesNotExist());
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getRegion()).isNull();
        assertThat(saved.getPassword()).isNotEqualTo(password);
        assertThat(encoder.matches(password, saved.getPassword())).isTrue();
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(saved));
        when(users.findById(42L)).thenReturn(Optional.of(saved));
        String response = mvc.perform(request("/api/auth/login", "user@example.com", password, "192.0.2.1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString())
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(response).get("accessToken").asText();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/users/me/region").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"region\":\"서울\",\"userId\":99}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.region").value("서울"));
        verify(users, never()).findById(99L);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"abc12345", "Abc1234567890!"})
    void signupAcceptsEightAndFourteenAsciiCharacters(String password) throws Exception {
        mvc.perform(request("/api/auth/signup", "user@example.com", password, "192.0.2.1"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"abc1234", "Abc12345678901!X", "가나다라마바사아", "abcd 123"})
    void signupRejectsInputsOutsideTheNewPolicy(String password) throws Exception {
        mvc.perform(request("/api/auth/signup", "user@example.com", password, "192.0.2.1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users);
    }

    @Test void oldHashStillAuthenticatesAndLongerPasswordSharingItsPrefixDoesNot() throws Exception {
        User user = new User("legacy@example.com", LEGACY_HASH);
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

    @Test void limitedSignupReturns429BeforeTouchingUsers() throws Exception {
        doThrow(new LoginRateLimitException(120)).when(rateLimiter)
                .consume("signup", "192.0.2.1", 10, Duration.ofHours(1));
        mvc.perform(request("/api/auth/signup", "user@example.com", "Password123!", "192.0.2.1"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "120"))
                .andExpect(jsonPath("$.message").isString());
        verifyNoInteractions(users);
    }

    @Test void nullAndBlankSignupPasswordsReturn400() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"region\":\"서울\",\"password\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(request("/api/auth/signup", "user@example.com", "        ", "192.0.2.1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users);
    }

    @Test void limitedLoginReturns429BeforeTouchingUsers() throws Exception {
        when(throttle.begin("unknown@example.com", "192.0.2.99")).thenThrow(new LoginRateLimitException(900));
        mvc.perform(request("/api/auth/login", "unknown@example.com", "wrong-password", "192.0.2.99"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.message").isString());
        verifyNoInteractions(users, attempt);
    }

    @Test void loginUsesThePeerAddressAndIgnoresForgedForwardingHeaders() throws Exception {
        mvc.perform(request("/api/auth/login", "unknown@example.com", "wrong-password", "192.0.2.1")
                        .header("CF-Connecting-IP", "192.0.2.10").header("X-Forwarded-For", "192.0.2.20"))
                .andExpect(status().isUnauthorized());
        verify(throttle).begin("unknown@example.com", "192.0.2.1");
        verify(attempt).failed();
        verify(attempt).close();
        verify(attempt, never()).succeeded();
    }
}
