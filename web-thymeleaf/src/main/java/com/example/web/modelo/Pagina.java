package com.example.web.modelo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Pagina de resultados con el formato de PaginaResponseDTO del backend. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Pagina<T>(List<T> content, int number, int size, long totalElements, int totalPages,
        boolean first, boolean last) {
}
