package com.example.backend.mapper;

import org.springframework.stereotype.Component;

import com.example.backend.dto.ProcesoCompartidoResponseDTO;
import com.example.backend.entity.ProcesoCompartido;

/**
 * Conversión de la entidad {@link ProcesoCompartido} a su DTO.
 */
@Component
public class ProcesoCompartidoMapper {

    public ProcesoCompartidoResponseDTO toDTO(ProcesoCompartido compartido) {
        ProcesoCompartidoResponseDTO dto = new ProcesoCompartidoResponseDTO();
        dto.setProcesoId(compartido.getProceso().getId());
        dto.setProcesoNombre(compartido.getProceso().getNombre());
        dto.setEmpresaDestinoId(compartido.getEmpresaDestino().getId());
        dto.setEmpresaDestinoNombre(compartido.getEmpresaDestino().getNombre());
        dto.setFechaCompartido(compartido.getFechaCompartido());
        return dto;
    }
}
