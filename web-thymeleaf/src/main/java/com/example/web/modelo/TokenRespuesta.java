package com.example.web.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Respuesta de POST /api/auth/login: el JWT y los datos del usuario. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenRespuesta(String token, String tipo, long expiraEnSegundos, Usuario usuario) {
}
