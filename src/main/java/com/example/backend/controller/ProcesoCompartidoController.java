package com.example.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.CompartirProcesoRequestDTO;
import com.example.backend.dto.ProcesoCompartidoResponseDTO;
import com.example.backend.service.ProcesoCompartidoService;
import com.example.backend.sesion.SesionActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/procesos")
@Tag(name = "Procesos compartidos", description = "HU-23: compartir el modelo con otras empresas en solo lectura")
public class ProcesoCompartidoController {

    private final ProcesoCompartidoService procesoCompartidoService;
    private final SesionActual sesionActual;

    public ProcesoCompartidoController(ProcesoCompartidoService procesoCompartidoService, SesionActual sesionActual) {
        this.procesoCompartidoService = procesoCompartidoService;
        this.sesionActual = sesionActual;
    }

    @GetMapping("/compartidos-conmigo")
    @Operation(summary = "Procesos de otras empresas compartidos con la mía")
    public ResponseEntity<List<ProcesoCompartidoResponseDTO>> compartidosConmigo(HttpSession session) {
        return ResponseEntity.ok(procesoCompartidoService.listarCompartidosConmigo(sesionActual.obtener(session)));
    }

    @GetMapping("/{procesoId}/comparticiones")
    @Operation(summary = "Empresas con las que está compartido un proceso propio")
    public ResponseEntity<List<ProcesoCompartidoResponseDTO>> listar(
            @PathVariable Long procesoId,
            HttpSession session) {
        return ResponseEntity.ok(procesoCompartidoService.listarComparticiones(procesoId, sesionActual.obtener(session)));
    }

    @PostMapping("/{procesoId}/comparticiones")
    @Operation(summary = "Compartir un proceso con otra empresa (solo administrador)")
    public ResponseEntity<ProcesoCompartidoResponseDTO> compartir(
            @PathVariable Long procesoId,
            @Valid @RequestBody CompartirProcesoRequestDTO request,
            HttpSession session) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(procesoCompartidoService.compartir(procesoId, request, sesionActual.obtener(session)));
    }

    @DeleteMapping("/{procesoId}/comparticiones/{empresaDestinoId}")
    @Operation(summary = "Dejar de compartir un proceso con una empresa")
    public ResponseEntity<Void> dejarDeCompartir(
            @PathVariable Long procesoId,
            @PathVariable Long empresaDestinoId,
            HttpSession session) {
        procesoCompartidoService.dejarDeCompartir(procesoId, empresaDestinoId, sesionActual.obtener(session));
        return ResponseEntity.noContent().build();
    }
}
