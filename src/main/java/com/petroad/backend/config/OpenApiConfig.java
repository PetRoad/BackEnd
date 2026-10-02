package com.petroad.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI publicApi(@Value("${app.public-url:}") String publicUrl) {
        OpenAPI api = new OpenAPI().info(new Info().title("PetRoad Swagger").version("0.0.1")
                .description("PetRoad 백엔드 API 개발 서버 문서"));
        api.components(new Components().addSecuritySchemes("bearerAuth",
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
        if (!publicUrl.isBlank()) api.setServers(List.of(new Server().url(publicUrl)));
        return api;
    }

    @Bean
    public OpenApiCustomizer apiAuthentication() {
        return api -> api.getPaths().forEach((path, item) -> {
            boolean protectedApi = path.startsWith("/api/") && !path.startsWith("/api/auth/")
                    && !path.equals("/api/hello") && !path.equals("/api/health");
            item.readOperations().forEach(operation -> operation.setSecurity(protectedApi
                    ? List.of(new SecurityRequirement().addList("bearerAuth")) : List.of()));
        });
    }
}
