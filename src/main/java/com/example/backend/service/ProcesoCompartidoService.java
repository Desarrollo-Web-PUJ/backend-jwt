package com.example.backend.service;

import java.util.List;

import com.example.backend.dto.CompartirProcesoRequestDTO;
import com.example.backend.dto.ProcesoCompartidoResponseDTO;
import com.example.backend.sesion.UsuarioSesion;

/**
 * HU-23: compartir el modelo de un proceso con otras empresas, solo en lectura.
 */
public interface ProcesoCompartidoService {

    List<ProcesoCompartidoResponseDTO> listarComparticiones(Long procesoId, UsuarioSesion sesion);

    ProcesoCompartidoResponseDTO compartir(Long procesoId, CompartirProcesoRequestDTO request, UsuarioSesion sesion);

    void dejarDeCompartir(Long procesoId, Long empresaDestinoId, UsuarioSesion sesion);

    List<ProcesoCompartidoResponseDTO> listarCompartidosConmigo(UsuarioSesion sesion);
}
