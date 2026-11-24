package com.example.web.modelo;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Usuario autenticado, tal como lo devuelve la API en el inicio de sesion. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Usuario(Long usuarioId, String nombre, String correo, String rol, Long empresaId)
        implements Serializable {

    public boolean puedeEditar() {
        return "ADMINISTRADOR".equals(rol) || "EDITOR".equals(rol);
    }
}
