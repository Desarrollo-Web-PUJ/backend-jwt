package com.example.backend.service;

import java.util.List;

import com.example.backend.dto.EliminacionLaneResponseDTO;
import com.example.backend.dto.LaneDTO;
import com.example.backend.dto.LaneRequestDTO;
import com.example.backend.dto.ReordenarLanesRequestDTO;
import com.example.backend.sesion.UsuarioSesion;

public interface LaneService {

    List<LaneDTO> listarPorPool(Long procesoId, Long poolId, UsuarioSesion sesion);

    // HU-22: crear, renombrar (reasignar rol), reordenar y eliminar lanes
    LaneDTO crearLane(Long procesoId, Long poolId, LaneRequestDTO request, UsuarioSesion sesion);

    LaneDTO editarLane(Long procesoId, Long poolId, Long laneId, LaneRequestDTO request, UsuarioSesion sesion);

    List<LaneDTO> reordenarLanes(Long procesoId, Long poolId, ReordenarLanesRequestDTO request, UsuarioSesion sesion);

    EliminacionLaneResponseDTO eliminarLane(Long procesoId, Long poolId, Long laneId, UsuarioSesion sesion);
}
