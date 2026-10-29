package com.example.backend.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.HistorialProcesoDTO;
import com.example.backend.dto.PaginaResponseDTO;
import com.example.backend.dto.ProcesoCrearRequestDTO;
import com.example.backend.dto.ProcesoDetalleDTO;
import com.example.backend.dto.ProcesoEditarRequestDTO;
import com.example.backend.dto.ProcesoListItemDTO;
import com.example.backend.dto.ProcesoResponseDTO;
import com.example.backend.entity.EstadoProceso;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.service.ProcesoService;
import com.example.backend.sesion.SesionActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/procesos")
@Tag(name = "Procesos", description = "HU-04 a HU-07: gestión de procesos, diagrama e historial")
public class ProcesoController {

    private final ProcesoService procesoService;
    private final SesionActual sesionActual;

    public ProcesoController(ProcesoService procesoService, SesionActual sesionActual) {
        this.procesoService = procesoService;
        this.sesionActual = sesionActual;
    }

    @GetMapping
    @Operation(summary = "Listar procesos de la empresa (paginado, con búsqueda y filtros)")
    public ResponseEntity<PaginaResponseDTO<ProcesoListItemDTO>> listar(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) EstadoProceso estado,
            @RequestParam(required = false) String categoria,
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @PageableDefault(size = 10, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable,
            HttpSession session) {

        return ResponseEntity.ok(procesoService.listarProcesos(
                sesionActual.obtener(session), nombre, estado, categoria, incluirInactivos, pageable));
    }

    @PostMapping
    @Operation(summary = "Crear un proceso (nace en borrador y con el pool de la empresa)")
    public ResponseEntity<ProcesoResponseDTO> crear(
            @Valid @RequestBody ProcesoCrearRequestDTO request,
            HttpSession session) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(procesoService.crearProceso(sesionActual.obtener(session), request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar el diagrama completo de un proceso (propio o compartido)")
    public ResponseEntity<ProcesoDetalleDTO> detalle(@PathVariable Long id, HttpSession session) {
        return ResponseEntity.ok(procesoService.obtenerDetalle(id, sesionActual.obtener(session)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar nombre, descripción, categoría y estado de un proceso")
    public ResponseEntity<ProcesoResponseDTO> editar(
            @PathVariable Long id,
            @Valid @RequestBody ProcesoEditarRequestDTO request,
            HttpSession session) {
        return ResponseEntity.ok(procesoService.editarProceso(id, sesionActual.obtener(session), request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar (lógicamente) un proceso; requiere confirmar=true")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean confirmar,
            HttpSession session) {
        if (!confirmar) {
            throw new OperacionInvalidaException("Debe confirmar la eliminación del proceso");
        }
        procesoService.eliminarProceso(id, sesionActual.obtener(session));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/historial")
    @Operation(summary = "Consultar el historial de cambios de un proceso")
    public ResponseEntity<List<HistorialProcesoDTO>> historial(@PathVariable Long id, HttpSession session) {
        return ResponseEntity.ok(procesoService.obtenerHistorial(id, sesionActual.obtener(session)));
    }
}
