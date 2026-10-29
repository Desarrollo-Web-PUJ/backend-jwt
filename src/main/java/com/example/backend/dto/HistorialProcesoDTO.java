package com.example.backend.dto;

import java.time.LocalDateTime;

/**
 * Entrada del historial de cambios de un proceso (HU-05, HU-07).
 */
public class HistorialProcesoDTO {

    private final Long id;
    private final String descripcion;
    private final String usuario;
    private final LocalDateTime fecha;

    public HistorialProcesoDTO(Long id, String descripcion, String usuario, LocalDateTime fecha) {
        this.id = id;
        this.descripcion = descripcion;
        this.usuario = usuario;
        this.fecha = fecha;
    }

    public Long getId() { return id; }
    public String getDescripcion() { return descripcion; }
    public String getUsuario() { return usuario; }
    public LocalDateTime getFecha() { return fecha; }
}
