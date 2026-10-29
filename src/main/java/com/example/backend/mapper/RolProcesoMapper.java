package com.example.backend.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.backend.dto.ProcesoUsoDTO;
import com.example.backend.dto.RolProcesoListItemDTO;
import com.example.backend.dto.RolProcesoResponseDTO;
import com.example.backend.dto.UsoRolProcesoDTO;
import com.example.backend.entity.RolProceso;

/**
 * Conversión de la entidad {@link RolProceso} a sus DTO.
 */
@Component
public class RolProcesoMapper {

    public RolProcesoResponseDTO toDTO(RolProceso rolProceso) {
        return new RolProcesoResponseDTO(
                rolProceso.getId(),
                rolProceso.getNombre(),
                rolProceso.getDescripcion()
        );
    }

    public RolProcesoListItemDTO toListItemDTO(RolProceso rolProceso, List<ProcesoUsoDTO> procesosEnUso) {
        boolean enUso = !procesosEnUso.isEmpty();
        return new RolProcesoListItemDTO(
                rolProceso.getId(),
                rolProceso.getNombre(),
                rolProceso.getDescripcion(),
                procesosEnUso,
                enUso,
                !enUso
        );
    }

    public ProcesoUsoDTO toProcesoUsoDTO(UsoRolProcesoDTO uso) {
        return new ProcesoUsoDTO(uso.procesoId(), uso.procesoNombre());
    }
}
