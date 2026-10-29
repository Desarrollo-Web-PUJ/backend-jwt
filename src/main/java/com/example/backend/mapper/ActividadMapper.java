package com.example.backend.mapper;

import org.springframework.stereotype.Component;

import com.example.backend.dto.ActividadDTO;
import com.example.backend.entity.Actividad;
import com.example.backend.entity.Lane;

/**
 * Conversión de la entidad {@link Actividad} a {@link ActividadDTO}.
 */
@Component
public class ActividadMapper {

    public ActividadDTO toDTO(Actividad actividad) {
        Lane lane = actividad.getLane();

        ActividadDTO dto = new ActividadDTO();
        dto.setId(actividad.getId());
        dto.setNombre(actividad.getNombre());
        dto.setTipo(actividad.getTipo());
        dto.setProcesoId(actividad.getProceso().getId());
        dto.setLaneId(lane.getId());
        dto.setLaneNombre(lane.getNombre());
        if (lane.getPool() != null) {
            dto.setPoolId(lane.getPool().getId());
        }
        dto.setPosicionX(actividad.getPosicionX());
        dto.setPosicionY(actividad.getPosicionY());
        return dto;
    }
}
