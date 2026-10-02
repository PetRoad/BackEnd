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
        "spring.flyway.enabled=false",
        "app.public-url=https://petroad-api.hrxlou.com",
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
    @MockBean private javax.sql.DataSource dataSource;

    @Test void bootBindsDefaultSecuritySettingsAndPublishesPasswordErrorsAndRetryAfter() throws Exception {
        String json = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("PetRoad Swagger"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['429'].headers['Retry-After']").exists())
                .andExpect(jsonPath("$.components.schemas.Login.properties.password.description")
                        .value(org.hamcrest.Matchers.containsString("72바이트")))
                .andExpect(jsonPath("$.components.schemas.Signup.properties.password.description")
                        .value(org.hamcrest.Matchers.containsString("72바이트")))
                .andExpect(jsonPath("$.servers[0].url").value("https://petroad-api.hrxlou.com"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/dog'].get.security[0].bearerAuth").exists())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(Path.of("target/openapi-security-verified.json"), json);
    }

    @Test void publicAndUnauthorizedApiResponsesCannotBeCached() throws Exception {
        mvc.perform(get("/api/hello")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/dog")).andExpect(status().is4xxClientError())
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
