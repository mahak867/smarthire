// ── SmartHire · src/main/java/com/smarthire/config/OpenApiConfig.java ──
package com.smarthire.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${smarthire.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI smartHireOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("SmartHire API")
                .version("1.0.0")
                .description("""
                    ## SmartHire — AI-Powered Recruitment Intelligence Platform

                    End-to-end hiring platform with two-stage AI candidate scoring:
                    **TF-IDF cosine similarity** for keyword matching + **Claude AI** for
                    narrative analysis and structured recommendation.

                    ### Authentication
                    All protected endpoints require a **Bearer JWT** token.
                    Obtain one via `POST /api/v1/auth/login`, then click **Authorize** above.

                    ### Rate Limits
                    | Endpoint | Limit |
                    |---|---|
                    | `POST /auth/login` | 5 req / 15 min per IP |
                    | `POST /auth/register` | 3 req / hour per IP |
                    | All other endpoints | 100 req / min per user |
                    """)
                .contact(new Contact()
                    .name("SmartHire Support")
                    .email("support@smarthire.app"))
                .license(new License()
                    .name("MIT")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server().url("/").description("Current server"),
                new Server().url(frontendUrl).description("Frontend")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
            .components(new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                    .name(BEARER_SCHEME)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Paste your access token here. Obtain one from POST /api/v1/auth/login")));
    }
}
