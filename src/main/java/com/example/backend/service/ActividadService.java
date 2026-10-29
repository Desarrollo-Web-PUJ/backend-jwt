package com.example.backend.service;

import java.util.List;

import com.example.backend.dto.ActividadDTO;
import com.example.backend.dto.ActividadRequestDTO;
import com.example.backend.dto.EliminacionActividadResponseDTO;
import com.example.backend.sesion.UsuarioSesion;

public interface ActividadService {

    List<ActividadDTO> listarPorProceso(Long procesoId, UsuarioSesion sesion);

    // HU-08: Crear actividad
    ActividadDTO crearActividad(Long procesoId, ActividadRequestDTO request, UsuarioSesion sesion);

    // HU-09: Editar actividad
    ActividadDTO editarActividad(Long procesoId, Long actividadId, ActividadRequestDTO request, UsuarioSesion sesion);

    // HU-10: Eliminar actividad
    EliminacionActividadResponseDTO eliminarActividad(Long procesoId, Long actividadId, UsuarioSesion sesion);
}
