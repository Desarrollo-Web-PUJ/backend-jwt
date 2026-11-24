package com.example.web.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Cambio registrado en el historial de un proceso. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Historial(Long id, String descripcion, String usuario, String fecha) {

    /** Fecha legible: "2026-11-24 13:05" en lugar de "2026-11-24T13:05:12.345". */
    public String fechaLegible() {
        if (fecha == null) {
            return "";
        }
        String texto = fecha.replace('T', ' ');
        return texto.length() > 16 ? texto.substring(0, 16) : texto;
    }
}
