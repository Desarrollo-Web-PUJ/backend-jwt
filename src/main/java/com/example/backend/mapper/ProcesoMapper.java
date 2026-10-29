package com.example.backend.mapper;

import org.springframework.stereotype.Component;

import com.example.backend.dto.ProcesoListItemDTO;
import com.example.backend.dto.ProcesoResponseDTO;
import com.example.backend.entity.Proceso;

/**
 * Conversión de la entidad {@link Proceso} a sus DTO de respuesta.
 */
@Component
public class ProcesoMapper {

    public ProcesoResponseDTO toResponseDTO(Proceso proceso) {
        return new ProcesoResponseDTO(
                proceso.getId(),
                proceso.getNombre(),
                proceso.getDescripcion(),
                proceso.getCategoria(),
                proceso.getEstado(),
                proceso.getActivo(),
                proceso.getEmpresa().getId()
        );
    }

    public ProcesoListItemDTO toListItemDTO(Proceso proceso) {
        return new ProcesoListItemDTO(
                proceso.getId(),
                proceso.getNombre(),
                proceso.getCategoria(),
                proceso.getEstado(),
                proceso.getActivo()
        );
    }
}
