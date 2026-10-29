package com.example.backend.service;

import java.util.List;

import com.example.backend.dto.EliminacionFlujoMensajeResponseDTO;
import com.example.backend.dto.FlujoMensajeRequestDTO;
import com.example.backend.dto.FlujoMensajeResponseDTO;
import com.example.backend.sesion.UsuarioSesion;

public interface FlujoMensajeService {

    List<FlujoMensajeResponseDTO> listarPorProceso(Long procesoId, UsuarioSesion sesion);

    // HU-25 / HU-26: mensaje de un Throw hacia un Catch de otro pool o hacia un pool externo
    FlujoMensajeResponseDTO crearFlujo(Long procesoId, FlujoMensajeRequestDTO request, UsuarioSesion sesion);

    FlujoMensajeResponseDTO editarFlujo(Long procesoId, Long flujoId, FlujoMensajeRequestDTO request,
                                        UsuarioSesion sesion);

    EliminacionFlujoMensajeResponseDTO eliminarFlujo(Long procesoId, Long flujoId, UsuarioSesion sesion);
}
