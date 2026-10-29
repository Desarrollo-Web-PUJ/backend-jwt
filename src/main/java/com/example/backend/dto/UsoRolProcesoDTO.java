package com.example.backend.dto;

/**
 * Proyección JPQL: un proceso en el que un rol de proceso está siendo usado
 * por alguna lane activa. Reemplaza el uso de filas Object[].
 */
public record UsoRolProcesoDTO(Long rolProcesoId, Long procesoId, String procesoNombre) {
}
