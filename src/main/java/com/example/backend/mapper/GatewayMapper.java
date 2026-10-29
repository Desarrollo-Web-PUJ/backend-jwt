package com.example.backend.mapper;

import org.springframework.stereotype.Component;

import com.example.backend.dto.GatewayResponseDTO;
import com.example.backend.entity.Gateway;

/**
 * Conversión de la entidad {@link Gateway} a {@link GatewayResponseDTO}.
 */
@Component
public class GatewayMapper {

    public GatewayResponseDTO toDTO(Gateway gateway) {
        GatewayResponseDTO dto = new GatewayResponseDTO();
        dto.setId(gateway.getId());
        dto.setProcesoId(gateway.getProceso().getId());
        if (gateway.getPool() != null) {
            dto.setPoolId(gateway.getPool().getId());
        }
        dto.setTipo(gateway.getTipo());
        dto.setPosicionX(gateway.getPosicionX());
        dto.setPosicionY(gateway.getPosicionY());
        return dto;
    }
}
