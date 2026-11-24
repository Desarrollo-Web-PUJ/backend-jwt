package com.example.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aplicacion web con vistas Thymeleaf (renderizadas en el servidor).
 *
 * No tiene base de datos propia: consume la API REST del backend con JWT,
 * igual que el frontend Angular. Es el tercer componente desplegable del
 * sistema, junto con el backend y el frontend.
 */
@SpringBootApplication
public class WebApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
