package com.example.backend.sesion;

import com.example.backend.entity.RolUsuario;

/**
 * Usuario autenticado de la petición: lo que necesitan los servicios para
 * aplicar permisos, aislar la información por empresa y registrar el historial.
 */
public record UsuarioSesion(Long usuarioId, String nombre, Long empresaId, RolUsuario rol) {
}
