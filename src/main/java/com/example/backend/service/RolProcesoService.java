package com.example.backend.service;

import org.springframework.data.domain.Pageable;

import com.example.backend.dto.EliminacionRolProcesoResponseDTO;
import com.example.backend.dto.PaginaResponseDTO;
import com.example.backend.dto.RolProcesoListItemDTO;
import com.example.backend.dto.RolProcesoRequestDTO;
import com.example.backend.dto.RolProcesoResponseDTO;
import com.example.backend.sesion.UsuarioSesion;

public interface RolProcesoService {

    // HU-17: Crear rol de proceso
    RolProcesoResponseDTO crear(RolProcesoRequestDTO request, UsuarioSesion sesion);

    // HU-18: Editar rol de proceso
    RolProcesoResponseDTO editar(Long rolProcesoId, RolProcesoRequestDTO request, UsuarioSesion sesion);

    // HU-19: Eliminar rol de proceso
    EliminacionRolProcesoResponseDTO eliminar(Long rolProcesoId, UsuarioSesion sesion);

    // HU-20: Consultar roles de proceso
    PaginaResponseDTO<RolProcesoListItemDTO> consultar(String nombre, Pageable pageable, UsuarioSesion sesion);
}
