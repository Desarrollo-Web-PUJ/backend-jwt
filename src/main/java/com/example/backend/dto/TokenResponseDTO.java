package com.example.backend.dto;

/**
 * Respuesta del inicio de sesión con JWT.
 *
 * @param token              token firmado que se envía en el encabezado Authorization
 * @param tipo               siempre "Bearer"
 * @param expiraEnSegundos   segundos de vigencia del token
 * @param usuario            datos del usuario autenticado
 */
public record TokenResponseDTO(String token, String tipo, long expiraEnSegundos, LoginResponseDTO usuario) {
}
