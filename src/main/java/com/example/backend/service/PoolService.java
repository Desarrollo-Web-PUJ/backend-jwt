package com.example.backend.service;

import java.util.List;

import com.example.backend.dto.EliminacionPoolResponseDTO;
import com.example.backend.dto.PoolRequestDTO;
import com.example.backend.dto.PoolResponseDTO;
import com.example.backend.dto.RolProcesoResponseDTO;
import com.example.backend.entity.Proceso;
import com.example.backend.sesion.UsuarioSesion;

public interface PoolService {

    List<PoolResponseDTO> listarPorProceso(Long procesoId, UsuarioSesion sesion);

    // HU-24: roles de proceso de la empresa, únicos asignables a las lanes del pool
    List<RolProcesoResponseDTO> listarRolesDisponibles(Long procesoId, Long poolId, UsuarioSesion sesion);

    PoolResponseDTO crearPool(Long procesoId, PoolRequestDTO request, UsuarioSesion sesion);

    PoolResponseDTO editarPool(Long procesoId, Long poolId, PoolRequestDTO request, UsuarioSesion sesion);

    EliminacionPoolResponseDTO eliminarPool(Long procesoId, Long poolId, UsuarioSesion sesion);

    // HU-21: pool de la empresa propietaria, creado junto con el proceso
    void crearPoolPropio(Proceso proceso);
}
