package com.example.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.web.sesion.RequiereSesionInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Cliente HTTP apuntando a la API REST (BACKEND_URL). */
    @Bean
    public RestClient api(@Value("${backend.url}") String urlBackend) {
        return RestClient.builder().baseUrl(urlBackend).build();
    }

    /** Las paginas de procesos exigen haber iniciado sesion. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequiereSesionInterceptor())
                .addPathPatterns("/", "/procesos", "/procesos/**");
    }
}
