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

import com.example.backend.dto.EliminacionFlujoMensajeResponseDTO;
import com.example.backend.dto.FlujoMensajeRequestDTO;
import com.example.backend.dto.FlujoMensajeResponseDTO;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.service.FlujoMensajeService;
import com.example.backend.sesion.SesionActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/procesos/{procesoId}/flujos-mensaje")
@Tag(name = "Flujos de mensaje", description = "HU-25 y HU-26: comunicación entre pools y con sistemas externos")
public class FlujoMensajeController {

    private final FlujoMensajeService flujoService;
    private final SesionActual sesionActual;

    public FlujoMensajeController(FlujoMensajeService flujoService, SesionActual sesionActual) {
        this.flujoService = flujoService;
        this.sesionActual = sesionActual;
    }

    @GetMapping
    @Operation(summary = "Listar los flujos de mensaje del proceso")
    public ResponseEntity<List<FlujoMensajeResponseDTO>> listar(@PathVariable Long procesoId, HttpSession session) {
        return ResponseEntity.ok(flujoService.listarPorProceso(procesoId, sesionActual.obtener(session)));
    }

    @PostMapping
    @Operation(summary = "Crear un flujo de mensaje desde un Message Throw")
    public ResponseEntity<FlujoMensajeResponseDTO> crear(
            @PathVariable Long procesoId,
            @Valid @RequestBody FlujoMensajeRequestDTO request,
            HttpSession session) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(flujoService.crearFlujo(procesoId, request, sesionActual.obtener(session)));
    }

    @PutMapping("/{flujoId}")
    @Operation(summary = "Editar un flujo de mensaje")
    public ResponseEntity<FlujoMensajeResponseDTO> editar(
            @PathVariable Long procesoId,
            @PathVariable Long flujoId,
            @Valid @RequestBody FlujoMensajeRequestDTO request,
            HttpSession session) {
        return ResponseEntity.ok(flujoService.editarFlujo(procesoId, flujoId, request, sesionActual.obtener(session)));
    }

    @DeleteMapping("/{flujoId}")
    @Operation(summary = "Eliminar un flujo de mensaje; requiere confirmar=true")
    public ResponseEntity<EliminacionFlujoMensajeResponseDTO> eliminar(
            @PathVariable Long procesoId,
            @PathVariable Long flujoId,
            @RequestParam(defaultValue = "false") boolean confirmar,
            HttpSession session) {
        if (!confirmar) {
            throw new OperacionInvalidaException("Debe confirmar la eliminación del flujo de mensaje");
        }
        return ResponseEntity.ok(flujoService.eliminarFlujo(procesoId, flujoId, sesionActual.obtener(session)));
    }
}
