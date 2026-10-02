package com.petroad.backend.security;

import com.petroad.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "jwt.secret=" + JwtServiceTest.KEY,
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
@AutoConfigureMockMvc
class OpenApiSecurityTest {
    @Autowired private MockMvc mvc;
    @MockBean private UserRepository users;
    @MockBean private DogRepository dogs;
    @MockBean private CourseRepository courses;
    @MockBean private CourseLikeRepository likes;
    @MockBean private WalkLogRepository logs;

    @Test void bootBindsDefaultSecuritySettingsAndPublishesPasswordErrorsAndRetryAfter() throws Exception {
        String json = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['429'].headers['Retry-After']").exists())
                .andExpect(jsonPath("$.components.schemas.Login.properties.password.description")
                        .value(org.hamcrest.Matchers.containsString("72바이트")))
                .andExpect(jsonPath("$.components.schemas.Signup.properties.password.description")
                        .value(org.hamcrest.Matchers.containsString("72바이트")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(Path.of("target/openapi-security-verified.json"), json);
    }
}
