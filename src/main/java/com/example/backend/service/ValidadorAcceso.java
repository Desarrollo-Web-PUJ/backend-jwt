package com.example.backend.service;

import org.springframework.stereotype.Component;

import com.example.backend.entity.Proceso;
import com.example.backend.entity.RolUsuario;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.exception.PermisoDenegadoException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.repository.ProcesoRepository;

/**
 * Reglas de acceso que comparten todos los servicios:
 * permisos según el rol de acceso del usuario y aislamiento por empresa.
 *
 * Antes cada servicio tenía su propia copia de estos métodos.
 */
@Component
public class ValidadorAcceso {

    private final ProcesoRepository procesoRepository;

    public ValidadorAcceso(ProcesoRepository procesoRepository) {
        this.procesoRepository = procesoRepository;
    }

    /** Administrador y editor pueden crear y modificar; solo lectura no. */
    public void exigirEdicion(RolUsuario rol) {
        if (rol != RolUsuario.ADMINISTRADOR && rol != RolUsuario.EDITOR) {
            throw new PermisoDenegadoException("No tiene permisos para modificar la información");
        }
    }

    /** Operaciones reservadas al administrador de la empresa. */
    public void exigirAdministrador(RolUsuario rol, String accion) {
        if (rol != RolUsuario.ADMINISTRADOR) {
            throw new PermisoDenegadoException("Solo un administrador puede " + accion);
        }
    }

    /** Proceso de la empresa del usuario (activo o inactivo). */
    public Proceso obtenerProceso(Long procesoId, Long empresaId) {
        return procesoRepository.findByIdAndEmpresaId(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El proceso no existe en la empresa indicada"));
    }

    /** Proceso de la empresa del usuario que todavía puede modificarse. */
    public Proceso obtenerProcesoActivo(Long procesoId, Long empresaId) {
        Proceso proceso = obtenerProceso(procesoId, empresaId);
        validarActivo(proceso);
        return proceso;
    }

    public void validarActivo(Proceso proceso) {
        if (!Boolean.TRUE.equals(proceso.getActivo())) {
            throw new OperacionInvalidaException("No se puede modificar un proceso eliminado");
        }
    }
}
