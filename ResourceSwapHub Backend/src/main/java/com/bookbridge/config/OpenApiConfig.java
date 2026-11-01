package com.bookbridge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI resourceSwapHubOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ResourceSwapHub API")
                        .version("v1")
                        .description("Platform where students discover, share and exchange educational resources. "
                                + "Most endpoints require a Bearer access token. "
                                + "Refresh tokens are delivered in an HttpOnly cookie by the auth endpoints.")
                        .contact(new Contact().name("ResourceSwapHub")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
