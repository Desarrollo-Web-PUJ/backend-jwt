package com.example.backend.mapper;

import org.springframework.stereotype.Component;

import com.example.backend.dto.ArcoResponseDTO;
import com.example.backend.entity.Arco;

/**
 * Conversión de la entidad {@link Arco} a {@link ArcoResponseDTO}.
 */
@Component
public class ArcoMapper {

    public ArcoResponseDTO toDTO(Arco arco) {
        ArcoResponseDTO dto = new ArcoResponseDTO();
        dto.setId(arco.getId());
        dto.setProcesoId(arco.getProceso().getId());
        dto.setTipoOrigen(arco.getTipoOrigen());
        dto.setOrigenId(arco.getOrigenId());
        dto.setTipoDestino(arco.getTipoDestino());
        dto.setDestinoId(arco.getDestinoId());
        dto.setEtiqueta(arco.getEtiqueta());
        dto.setCondicion(arco.getCondicion());
        return dto;
    }
}
