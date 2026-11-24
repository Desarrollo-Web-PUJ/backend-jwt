package com.example.backend.seguridad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.example.backend.dto.ErrorResponseDTO;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escribe los errores de seguridad (401 y 403) con el mismo formato
 * {mensaje, errores} que usa el resto de la API.
 *
 * Hace falta porque estos errores ocurren en los filtros de Spring Security,
 * antes de llegar a los controladores y al GlobalExceptionHandler.
 */
@Component
public class RespuestaErrorSeguridad {

    private final JsonMapper jsonMapper;

    public RespuestaErrorSeguridad(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void escribir(HttpServletResponse response, int estado, String mensaje) throws IOException {
        response.setStatus(estado);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(ErrorResponseDTO.de(mensaje)));
    }
}
