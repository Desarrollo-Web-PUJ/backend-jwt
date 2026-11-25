package com.example.backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.backend.entity.Proceso;
import com.example.backend.entity.RolUsuario;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.exception.PermisoDenegadoException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.repository.ProcesoRepository;

/** Pruebas unitarias de las reglas de acceso compartidas (rol y empresa), con el repositorio simulado. */
@ExtendWith(MockitoExtension.class)
class ValidadorAccesoTest {

    @Mock
    private ProcesoRepository procesoRepository;

    @InjectMocks
    private ValidadorAcceso validador;

    @Test
    @DisplayName("Administrador y editor pueden editar")
    void edicionPermitida() {
        assertDoesNotThrow(() -> validador.exigirEdicion(RolUsuario.ADMINISTRADOR));
        assertDoesNotThrow(() -> validador.exigirEdicion(RolUsuario.EDITOR));
    }

    @Test
    @DisplayName("Solo lectura no puede editar")
    void lecturaNoEdita() {
        assertThrows(PermisoDenegadoException.class, () -> validador.exigirEdicion(RolUsuario.LECTURA));
    }

    @Test
    @DisplayName("Las acciones de administrador se niegan al editor con un mensaje que nombra la acción")
    void soloAdministrador() {
        assertDoesNotThrow(() -> validador.exigirAdministrador(RolUsuario.ADMINISTRADOR, "eliminar procesos"));
        PermisoDenegadoException e = assertThrows(PermisoDenegadoException.class,
                () -> validador.exigirAdministrador(RolUsuario.EDITOR, "eliminar procesos"));
        assertTrue(e.getMessage().contains("eliminar procesos"));
    }

    @Test
    @DisplayName("Un proceso de otra empresa no se encuentra")
    void procesoDeOtraEmpresa() {
        when(procesoRepository.findByIdAndEmpresaId(5L, 2L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> validador.obtenerProceso(5L, 2L));
    }

    @Test
    @DisplayName("Un proceso eliminado no se puede modificar")
    void procesoInactivo() {
        Proceso proceso = new Proceso();
        proceso.setActivo(false);
        when(procesoRepository.findByIdAndEmpresaId(5L, 1L)).thenReturn(Optional.of(proceso));
        assertThrows(OperacionInvalidaException.class, () -> validador.obtenerProcesoActivo(5L, 1L));
    }

    @Test
    @DisplayName("Un proceso activo de la empresa se devuelve")
    void procesoActivo() {
        Proceso proceso = new Proceso();
        when(procesoRepository.findByIdAndEmpresaId(5L, 1L)).thenReturn(Optional.of(proceso));
        assertSame(proceso, validador.obtenerProcesoActivo(5L, 1L));
    }
}
