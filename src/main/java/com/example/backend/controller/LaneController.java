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

import com.example.backend.dto.EliminacionLaneResponseDTO;
import com.example.backend.dto.LaneDTO;
import com.example.backend.dto.LaneRequestDTO;
import com.example.backend.dto.ReordenarLanesRequestDTO;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.service.LaneService;
import com.example.backend.sesion.SesionActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/procesos/{procesoId}/pools/{poolId}/lanes")
@Tag(name = "Lanes", description = "HU-22: divisiones del pool asociadas a un rol de proceso")
public class LaneController {

    private final LaneService laneService;
    private final SesionActual sesionActual;

    public LaneController(LaneService laneService, SesionActual sesionActual) {
        this.laneService = laneService;
        this.sesionActual = sesionActual;
    }

    @GetMapping
    @Operation(summary = "Listar las lanes del pool en orden")
    public ResponseEntity<List<LaneDTO>> listar(
            @PathVariable Long procesoId,
            @PathVariable Long poolId,
            HttpSession session) {
        return ResponseEntity.ok(laneService.listarPorPool(procesoId, poolId, sesionActual.obtener(session)));
    }

    @PostMapping
    @Operation(summary = "Crear una lane asociada a un rol de proceso")
    public ResponseEntity<LaneDTO> crear(
            @PathVariable Long procesoId,
            @PathVariable Long poolId,
            @Valid @RequestBody LaneRequestDTO request,
            HttpSession session) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(laneService.crearLane(procesoId, poolId, request, sesionActual.obtener(session)));
    }

    @PutMapping("/orden")
    @Operation(summary = "Reordenar todas las lanes del pool")
    public ResponseEntity<List<LaneDTO>> reordenar(
            @PathVariable Long procesoId,
            @PathVariable Long poolId,
            @Valid @RequestBody ReordenarLanesRequestDTO request,
            HttpSession session) {
        return ResponseEntity.ok(laneService.reordenarLanes(procesoId, poolId, request, sesionActual.obtener(session)));
    }

    @PutMapping("/{laneId}")
    @Operation(summary = "Editar una lane (reasignar su rol o su posición)")
    public ResponseEntity<LaneDTO> editar(
            @PathVariable Long procesoId,
            @PathVariable Long poolId,
            @PathVariable Long laneId,
            @Valid @RequestBody LaneRequestDTO request,
            HttpSession session) {
        return ResponseEntity.ok(laneService.editarLane(procesoId, poolId, laneId, request, sesionActual.obtener(session)));
    }

    @DeleteMapping("/{laneId}")
    @Operation(summary = "Eliminar una lane sin actividades; requiere confirmar=true")
    public ResponseEntity<EliminacionLaneResponseDTO> eliminar(
            @PathVariable Long procesoId,
            @PathVariable Long poolId,
            @PathVariable Long laneId,
            @RequestParam(defaultValue = "false") boolean confirmar,
            HttpSession session) {
        if (!confirmar) {
            throw new OperacionInvalidaException("Debe confirmar la eliminación de la lane");
        }
        return ResponseEntity.ok(laneService.eliminarLane(procesoId, poolId, laneId, sesionActual.obtener(session)));
    }
}
