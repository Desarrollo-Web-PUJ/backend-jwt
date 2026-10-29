package com.example.backend.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.example.backend.dto.HistorialProcesoDTO;
import com.example.backend.dto.PaginaResponseDTO;
import com.example.backend.dto.ProcesoCrearRequestDTO;
import com.example.backend.dto.ProcesoDetalleDTO;
import com.example.backend.dto.ProcesoEditarRequestDTO;
import com.example.backend.dto.ProcesoListItemDTO;
import com.example.backend.dto.ProcesoResponseDTO;
import com.example.backend.entity.EstadoProceso;
import com.example.backend.sesion.UsuarioSesion;

public interface ProcesoService {

    // HU-04
    ProcesoResponseDTO crearProceso(UsuarioSesion sesion, ProcesoCrearRequestDTO request);

    // HU-05
    ProcesoResponseDTO editarProceso(Long procesoId, UsuarioSesion sesion, ProcesoEditarRequestDTO request);

    // HU-06
    void eliminarProceso(Long procesoId, UsuarioSesion sesion);

    // HU-07: listado paginado con búsqueda y filtros
    PaginaResponseDTO<ProcesoListItemDTO> listarProcesos(
            UsuarioSesion sesion,
            String nombre,
            EstadoProceso estado,
            String categoria,
            boolean incluirInactivos,
            Pageable pageable);

    // HU-07: diagrama completo (propio o compartido en solo lectura, HU-23)
    ProcesoDetalleDTO obtenerDetalle(Long procesoId, UsuarioSesion sesion);

    // HU-07: historial de cambios
    List<HistorialProcesoDTO> obtenerHistorial(Long procesoId, UsuarioSesion sesion);
}
