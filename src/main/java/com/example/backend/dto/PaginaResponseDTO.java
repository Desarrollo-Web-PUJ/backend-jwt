package com.example.backend.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Respuesta paginada estable para los listados de la API.
 *
 * Se usa en lugar de serializar directamente {@link Page}: el formato JSON de
 * PageImpl no es estable entre versiones de Spring Data, y este contrato sí.
 */
public record PaginaResponseDTO<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PaginaResponseDTO<T> desde(Page<T> pagina) {
        return new PaginaResponseDTO<>(
                pagina.getContent(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages(),
                pagina.isFirst(),
                pagina.isLast()
        );
    }
}
