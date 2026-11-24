package com.example.web.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Fila del listado de procesos. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcesoResumen(Long id, String nombre, String categoria, String estado, Boolean activo) {
}
