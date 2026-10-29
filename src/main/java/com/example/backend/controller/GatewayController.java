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

import com.example.backend.dto.EdicionGatewayResponseDTO;
import com.example.backend.dto.EliminacionGatewayResponseDTO;
import com.example.backend.dto.GatewayRequestDTO;
import com.example.backend.dto.GatewayResponseDTO;
import com.example.backend.service.GatewayService;
import com.example.backend.sesion.SesionActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/procesos/{procesoId}/gateways")
@Tag(name = "Gateways", description = "HU-14 a HU-16: puntos de decisión exclusivos, paralelos o inclusivos")
public class GatewayController {

    private final GatewayService gatewayService;
    private final SesionActual sesionActual;

    public GatewayController(GatewayService gatewayService, SesionActual sesionActual) {
        this.gatewayService = gatewayService;
        this.sesionActual = sesionActual;
    }

    @GetMapping
    @Operation(summary = "Listar los gateways del proceso")
    public ResponseEntity<List<GatewayResponseDTO>> listar(@PathVariable Long procesoId, HttpSession session) {
        return ResponseEntity.ok(gatewayService.listarPorProceso(procesoId, sesionActual.obtener(session)));
    }

    @PostMapping
    @Operation(summary = "Crear un gateway en un pool")
    public ResponseEntity<GatewayResponseDTO> crear(
            @PathVariable Long procesoId,
            @Valid @RequestBody GatewayRequestDTO request,
            HttpSession session) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gatewayService.crearGateway(procesoId, request, sesionActual.obtener(session)));
    }

    @PutMapping("/{gatewayId}")
    @Operation(summary = "Editar un gateway (devuelve advertencias de coherencia)")
    public ResponseEntity<EdicionGatewayResponseDTO> editar(
            @PathVariable Long procesoId,
            @PathVariable Long gatewayId,
            @Valid @RequestBody GatewayRequestDTO request,
            HttpSession session) {
        return ResponseEntity.ok(
                gatewayService.editarGateway(procesoId, gatewayId, request, sesionActual.obtener(session)));
    }

    @DeleteMapping("/{gatewayId}")
    @Operation(summary = "Eliminar un gateway y sus arcos; sin confirmar=true responde qué se perdería")
    public ResponseEntity<EliminacionGatewayResponseDTO> eliminar(
            @PathVariable Long procesoId,
            @PathVariable Long gatewayId,
            @RequestParam(defaultValue = "false") boolean confirmar,
            HttpSession session) {
        return ResponseEntity.ok(
                gatewayService.eliminarGateway(procesoId, gatewayId, confirmar, sesionActual.obtener(session)));
    }
}
