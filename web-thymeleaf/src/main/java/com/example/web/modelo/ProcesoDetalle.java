package com.example.web.modelo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Proceso con los elementos de su diagrama (GET /api/procesos/{id}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcesoDetalle(
        Long id,
        String nombre,
        String descripcion,
        String categoria,
        String estado,
        Boolean activo,
        Long empresaId,
        List<Elemento> pools,
        List<Elemento> lanes,
        List<Elemento> actividades,
        List<Elemento> gateways,
        List<Elemento> arcos,
        List<Elemento> eventosMensaje,
        List<Elemento> flujosMensaje) {

    /** Elemento del diagrama con los campos que muestra la vista; el resto se ignora. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Elemento(Long id, String nombre, String tipo, String laneNombre, String nombreMensaje,
            String etiqueta) {
    }
}
