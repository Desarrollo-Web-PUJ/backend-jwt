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

import com.example.backend.dto.EliminacionEventoMensajeResponseDTO;
import com.example.backend.dto.EventoMensajeRequestDTO;
import com.example.backend.dto.EventoMensajeResponseDTO;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.service.EventoMensajeService;
import com.example.backend.sesion.SesionActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/procesos/{procesoId}/eventos-mensaje")
@Tag(name = "Eventos de mensaje", description = "HU-25 a HU-28: Message Throw, Message Catch y correlación")
public class EventoMensajeController {

    private final EventoMensajeService eventoService;
    private final SesionActual sesionActual;

    public EventoMensajeController(EventoMensajeService eventoService, SesionActual sesionActual) {
        this.eventoService = eventoService;
        this.sesionActual = sesionActual;
    }

    @GetMapping
    @Operation(summary = "Listar los eventos de mensaje del proceso")
    public ResponseEntity<List<EventoMensajeResponseDTO>> listar(@PathVariable Long procesoId, HttpSession session) {
        return ResponseEntity.ok(eventoService.listarPorProceso(procesoId, sesionActual.obtener(session)));
    }

    @PostMapping
    @Operation(summary = "Crear un evento de mensaje (devuelve advertencias de modelado)")
    public ResponseEntity<EventoMensajeResponseDTO> crear(
            @PathVariable Long procesoId,
            @Valid @RequestBody EventoMensajeRequestDTO request,
            HttpSession session) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventoService.crearEvento(procesoId, request, sesionActual.obtener(session)));
    }

    @PutMapping("/{eventoId}")
    @Operation(summary = "Editar un evento de mensaje")
    public ResponseEntity<EventoMensajeResponseDTO> editar(
            @PathVariable Long procesoId,
            @PathVariable Long eventoId,
            @Valid @RequestBody EventoMensajeRequestDTO request,
            HttpSession session) {
        return ResponseEntity.ok(eventoService.editarEvento(procesoId, eventoId, request, sesionActual.obtener(session)));
    }

    @DeleteMapping("/{eventoId}")
    @Operation(summary = "Eliminar un evento de mensaje con sus flujos y arcos; requiere confirmar=true")
    public ResponseEntity<EliminacionEventoMensajeResponseDTO> eliminar(
            @PathVariable Long procesoId,
            @PathVariable Long eventoId,
            @RequestParam(defaultValue = "false") boolean confirmar,
            HttpSession session) {
        if (!confirmar) {
            throw new OperacionInvalidaException("Debe confirmar la eliminación del evento");
        }
        return ResponseEntity.ok(eventoService.eliminarEvento(procesoId, eventoId, sesionActual.obtener(session)));
    }
}
