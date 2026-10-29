package com.example.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.backend.dto.UsoRolProcesoDTO;
import com.example.backend.entity.Lane;

public interface LaneRepository extends JpaRepository<Lane, Long> {

    List<Lane> findByProcesoIdAndActivoTrueOrderByOrdenAsc(Long procesoId);

    // Procesos en los que cada rol está siendo usado por alguna lane activa.
    // La consulta construye directamente el DTO (proyección por constructor).
    @Query("""
            SELECT DISTINCT new com.example.backend.dto.UsoRolProcesoDTO(
                l.rolProceso.id, p.id, p.nombre)
            FROM Lane l
            JOIN l.proceso p
            WHERE l.rolProceso.id IN :rolIds
            AND l.activo = true
            ORDER BY p.nombre
            """)
    List<UsoRolProcesoDTO> findUsoDeRoles(@Param("rolIds") Collection<Long> rolIds);

    // HU-22: lanes de un pool, ordenadas
    List<Lane> findByPoolIdAndActivoTrueOrderByOrdenAsc(Long poolId);

    long countByPoolIdAndActivoTrue(Long poolId);

    Optional<Lane> findByIdAndPoolId(Long id, Long poolId);

    Optional<Lane> findByIdAndPoolIdAndActivoTrue(Long id, Long poolId);

    boolean existsByPoolIdAndRolProcesoIdAndActivoTrue(Long poolId, Long rolProcesoId);

    // Uso de un rol de proceso en cualquier lane activa.
    boolean existsByRolProcesoIdAndActivoTrue(Long rolProcesoId);
}
