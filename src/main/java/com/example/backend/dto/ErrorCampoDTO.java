package com.example.backend.dto;

/**
 * Error de validación asociado a un campo de la petición.
 */
public record ErrorCampoDTO(String campo, String mensaje) {
}
