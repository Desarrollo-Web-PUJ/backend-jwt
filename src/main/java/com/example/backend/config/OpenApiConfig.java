package com.example.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos de la documentación OpenAPI que publica springdoc en
 * /v3/api-docs y que muestra Swagger UI en /swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiProcesos() {
        return new OpenAPI()
                .info(new Info()
                        .title("API - Editor y visualizador de procesos")
                        .version("2.0")
                        .description("""
                                Servicios REST del sistema de gestión de procesos multiempresa.
                                La autenticación usa la sesión HTTP: primero ejecute
                                POST /api/auth/login y luego el resto de peticiones
                                viajan con la cookie JSESSIONID."""));
    }
}
