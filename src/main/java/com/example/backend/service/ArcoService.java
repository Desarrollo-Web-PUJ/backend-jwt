package com.example.backend.service;

import java.util.List;

import com.example.backend.dto.ArcoRequestDTO;
import com.example.backend.dto.ArcoResponseDTO;
import com.example.backend.dto.EliminacionArcoResponseDTO;
import com.example.backend.sesion.UsuarioSesion;

public interface ArcoService {

    List<ArcoResponseDTO> listarPorProceso(Long procesoId, UsuarioSesion sesion);

    // HU-11: Crear arco
    ArcoResponseDTO crearArco(Long procesoId, ArcoRequestDTO request, UsuarioSesion sesion);

    // HU-12: Editar arco
    ArcoResponseDTO editarArco(Long procesoId, Long arcoId, ArcoRequestDTO request, UsuarioSesion sesion);

    // HU-13: Eliminar arco
    EliminacionArcoResponseDTO eliminarArco(Long procesoId, Long arcoId, UsuarioSesion sesion);
}
