package com.example.backend.service;

import java.util.List;

import com.example.backend.dto.EliminacionEventoMensajeResponseDTO;
import com.example.backend.dto.EventoMensajeRequestDTO;
import com.example.backend.dto.EventoMensajeResponseDTO;
import com.example.backend.sesion.UsuarioSesion;

public interface EventoMensajeService {

    List<EventoMensajeResponseDTO> listarPorProceso(Long procesoId, UsuarioSesion sesion);

    // HU-25 (throw), HU-26 (envío externo), HU-27 (catch), HU-28 (correlación)
    EventoMensajeResponseDTO crearEvento(Long procesoId, EventoMensajeRequestDTO request, UsuarioSesion sesion);

    EventoMensajeResponseDTO editarEvento(Long procesoId, Long eventoId, EventoMensajeRequestDTO request,
                                          UsuarioSesion sesion);

    EliminacionEventoMensajeResponseDTO eliminarEvento(Long procesoId, Long eventoId, UsuarioSesion sesion);
}
