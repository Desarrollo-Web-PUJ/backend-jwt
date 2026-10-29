package com.example.backend.mapper;

import org.springframework.stereotype.Component;

import com.example.backend.dto.FlujoMensajeResponseDTO;
import com.example.backend.entity.FlujoMensaje;

/**
 * Conversión de la entidad {@link FlujoMensaje} a {@link FlujoMensajeResponseDTO}.
 */
@Component
public class FlujoMensajeMapper {

    public FlujoMensajeResponseDTO toDTO(FlujoMensaje flujo) {
        FlujoMensajeResponseDTO dto = new FlujoMensajeResponseDTO();
        dto.setId(flujo.getId());
        dto.setProcesoId(flujo.getProceso().getId());
        dto.setOrigenId(flujo.getOrigen().getId());
        if (flujo.getDestino() != null) {
            dto.setDestinoId(flujo.getDestino().getId());
        }
        if (flujo.getPoolDestino() != null) {
            dto.setPoolDestinoId(flujo.getPoolDestino().getId());
        }
        dto.setEtiqueta(flujo.getEtiqueta());
        return dto;
    }
}
