package com.example.backend.mapper;

import org.springframework.stereotype.Component;

import com.example.backend.dto.HistorialProcesoDTO;
import com.example.backend.entity.HistorialProceso;

/**
 * Conversión de la entidad {@link HistorialProceso} a {@link HistorialProcesoDTO}.
 */
@Component
public class HistorialProcesoMapper {

    public HistorialProcesoDTO toDTO(HistorialProceso historial) {
        return new HistorialProcesoDTO(
                historial.getId(),
                historial.getDescripcion(),
                historial.getUsuarioNombre(),
                historial.getFecha()
        );
    }
}
