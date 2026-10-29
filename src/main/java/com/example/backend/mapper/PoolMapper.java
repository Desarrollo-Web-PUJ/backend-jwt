package com.example.backend.mapper;

import org.springframework.stereotype.Component;

import com.example.backend.dto.PoolResponseDTO;
import com.example.backend.entity.Pool;

/**
 * Conversión de la entidad {@link Pool} a {@link PoolResponseDTO}.
 * La cantidad de lanes la calcula quien llama, para que el mapper no
 * dependa de la capa de persistencia.
 */
@Component
public class PoolMapper {

    public PoolResponseDTO toDTO(Pool pool, long cantidadLanes) {
        PoolResponseDTO dto = new PoolResponseDTO();
        dto.setId(pool.getId());
        dto.setNombre(pool.getNombre());
        dto.setTipo(pool.getTipo());
        dto.setProcesoId(pool.getProceso().getId());
        dto.setCantidadLanes(Math.toIntExact(cantidadLanes));
        return dto;
    }
}
