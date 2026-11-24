package com.example.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.backend.seguridad.JwtAuthenticationFilter;
import com.example.backend.seguridad.JwtService;
import com.example.backend.seguridad.RespuestaErrorSeguridad;
import com.example.backend.service.AuthService;

/**
 * Configuración de Spring Security.
 *
 * - API sin estado (STATELESS): no hay sesión HTTP; cada petición trae su JWT.
 * - CSRF desactivado: el token viaja en un encabezado que el navegador no envía
 *   solo, así que no aplica el ataque CSRF de las cookies.
 * - Rutas públicas: inicio de sesión, registro de empresa y Swagger.
 * - Autorización por rol (primera barrera). Cada servicio además verifica la
 *   empresa del usuario (ValidadorAcceso), que garantiza el aislamiento de la
 *   información entre organizaciones.
 * - 401 y 403 se responden con el formato {mensaje, errores} de la API.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ADMINISTRADOR = "ADMINISTRADOR";
    private static final String EDITOR = "EDITOR";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, JwtService jwtService,
            AuthService authService, RespuestaErrorSeguridad respuestaError) throws Exception {

        JwtAuthenticationFilter filtroJwt = new JwtAuthenticationFilter(jwtService, authService, respuestaError);

        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(rutas -> rutas
                // Públicas
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/token", "/api/empresas/registro").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                // Solo administrador
                .requestMatchers("/api/empresas/*/usuarios/**").hasRole(ADMINISTRADOR)
                .requestMatchers(HttpMethod.POST, "/api/roles-proceso/**").hasRole(ADMINISTRADOR)
                .requestMatchers(HttpMethod.PUT, "/api/roles-proceso/**").hasRole(ADMINISTRADOR)
                .requestMatchers(HttpMethod.DELETE, "/api/roles-proceso/**").hasRole(ADMINISTRADOR)
                .requestMatchers("/api/procesos/*/comparticiones/**").hasRole(ADMINISTRADOR)
                .requestMatchers(HttpMethod.DELETE, "/api/procesos/*").hasRole(ADMINISTRADOR)
                // Modelar procesos: administrador o editor
                .requestMatchers(HttpMethod.POST, "/api/procesos/**").hasAnyRole(ADMINISTRADOR, EDITOR)
                .requestMatchers(HttpMethod.PUT, "/api/procesos/**").hasAnyRole(ADMINISTRADOR, EDITOR)
                .requestMatchers(HttpMethod.PATCH, "/api/procesos/**").hasAnyRole(ADMINISTRADOR, EDITOR)
                .requestMatchers(HttpMethod.DELETE, "/api/procesos/**").hasAnyRole(ADMINISTRADOR, EDITOR)
                // Todo lo demás: cualquier usuario autenticado
                .anyRequest().authenticated())
            .exceptionHandling(errores -> errores
                .authenticationEntryPoint((request, response, ex) -> respuestaError.escribir(response,
                        HttpStatus.UNAUTHORIZED.value(), "Debe iniciar sesión para realizar esta operación"))
                .accessDeniedHandler((request, response, ex) -> respuestaError.escribir(response,
                        HttpStatus.FORBIDDEN.value(), "No tiene permisos para realizar esta operación")))
            .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
