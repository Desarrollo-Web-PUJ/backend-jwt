package com.example.backend.controller;

import java.util.List;

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

import com.example.backend.dto.EliminacionPoolResponseDTO;
import com.example.backend.dto.PoolRequestDTO;
import com.example.backend.dto.PoolResponseDTO;
import com.example.backend.dto.RolProcesoResponseDTO;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.service.PoolService;
import com.example.backend.sesion.SesionActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/procesos/{procesoId}/pools")
@Tag(name = "Pools", description = "HU-21 y HU-24: participantes del proceso")
public class PoolController {

    private final PoolService poolService;
    private final SesionActual sesionActual;

    public PoolController(PoolService poolService, SesionActual sesionActual) {
        this.poolService = poolService;
        this.sesionActual = sesionActual;
    }

    @GetMapping
    @Operation(summary = "Listar los pools del proceso")
    public ResponseEntity<List<PoolResponseDTO>> listar(@PathVariable Long procesoId, HttpSession session) {
        return ResponseEntity.ok(poolService.listarPorProceso(procesoId, sesionActual.obtener(session)));
    }

    @GetMapping("/{poolId}/roles-disponibles")
    @Operation(summary = "Roles de proceso que pueden asignarse a las lanes del pool")
    public ResponseEntity<List<RolProcesoResponseDTO>> rolesDisponibles(
            @PathVariable Long procesoId,
            @PathVariable Long poolId,
            HttpSession session) {
        return ResponseEntity.ok(poolService.listarRolesDisponibles(procesoId, poolId, sesionActual.obtener(session)));
    }

    @PostMapping
    @Operation(summary = "Crear un pool (propio o externo / caja negra)")
    public ResponseEntity<PoolResponseDTO> crear(
            @PathVariable Long procesoId,
            @Valid @RequestBody PoolRequestDTO request,
            HttpSession session) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(poolService.crearPool(procesoId, request, sesionActual.obtener(session)));
    }

    @PutMapping("/{poolId}")
    @Operation(summary = "Editar un pool")
    public ResponseEntity<PoolResponseDTO> editar(
            @PathVariable Long procesoId,
            @PathVariable Long poolId,
            @Valid @RequestBody PoolRequestDTO request,
            HttpSession session) {
        return ResponseEntity.ok(poolService.editarPool(procesoId, poolId, request, sesionActual.obtener(session)));
    }

    @DeleteMapping("/{poolId}")
    @Operation(summary = "Eliminar (lógicamente) un pool vacío; requiere confirmar=true")
    public ResponseEntity<EliminacionPoolResponseDTO> eliminar(
            @PathVariable Long procesoId,
            @PathVariable Long poolId,
            @RequestParam(defaultValue = "false") boolean confirmar,
            HttpSession session) {
        if (!confirmar) {
            throw new OperacionInvalidaException("Debe confirmar la eliminación del pool");
        }
        return ResponseEntity.ok(poolService.eliminarPool(procesoId, poolId, sesionActual.obtener(session)));
    }
}
