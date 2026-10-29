package com.example.backend.service;

import java.util.List;

import com.example.backend.dto.HistorialProcesoDTO;
import com.example.backend.entity.Proceso;
import com.example.backend.sesion.UsuarioSesion;

/**
 * Registro y consulta del historial de cambios de un proceso.
 * Centraliza una responsabilidad que antes repetía cada servicio y deja
 * constancia del usuario que hizo cada cambio y la fecha (HU-05).
 */
public interface HistorialService {

    void registrar(Proceso proceso, UsuarioSesion autor, String descripcion);

    List<HistorialProcesoDTO> listarPorProceso(Long procesoId);
}
