package com.example.backend.mapper;

import org.springframework.stereotype.Component;

import com.example.backend.dto.LaneDTO;
import com.example.backend.entity.Lane;

/**
 * Conversión de la entidad {@link Lane} a {@link LaneDTO}.
 */
@Component
public class LaneMapper {

    public LaneDTO toDTO(Lane lane) {
        LaneDTO dto = new LaneDTO(lane.getId(), lane.getNombre());
        dto.setProcesoId(lane.getProceso().getId());
        if (lane.getPool() != null) {
            dto.setPoolId(lane.getPool().getId());
        }
        if (lane.getRolProceso() != null) {
            dto.setRolProcesoId(lane.getRolProceso().getId());
        }
        dto.setOrden(lane.getOrden());
        return dto;
    }
}
