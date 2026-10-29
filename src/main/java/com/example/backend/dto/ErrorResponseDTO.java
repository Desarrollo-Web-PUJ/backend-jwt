package com.example.backend.dto;

import java.util.List;

/**
 * Cuerpo estándar de todas las respuestas de error de la API.
 *
 * @param mensaje descripción legible del problema
 * @param errores errores puntuales por campo (solo en validaciones; vacío en los demás casos)
 */
public record ErrorResponseDTO(String mensaje, List<ErrorCampoDTO> errores) {

    public ErrorResponseDTO {
        errores = errores == null ? List.of() : List.copyOf(errores);
    }

    public static ErrorResponseDTO de(String mensaje) {
        return new ErrorResponseDTO(mensaje, List.of());
    }
}
