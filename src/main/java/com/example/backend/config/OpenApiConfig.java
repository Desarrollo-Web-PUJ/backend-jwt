package com.example.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos de la documentación OpenAPI que publica springdoc en
 * /v3/api-docs y que muestra Swagger UI en /swagger-ui.html.
 * Incluye el esquema "bearer" para probar el JWT con el botón Authorize.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_JWT = "bearer-jwt";

    @Bean
    public OpenAPI apiProcesos() {
        return new OpenAPI()
                .info(new Info()
                        .title("API - Editor y visualizador de procesos")
                        .version("2.1")
                        .description("""
                                Servicios REST del sistema de gestión de procesos multiempresa.
                                Autenticación con JWT: ejecute POST /api/auth/token, copie el
                                token, pulse Authorize y péguelo. También se acepta la sesión
                                HTTP (POST /api/auth/login) que usa el frontend."""))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}
