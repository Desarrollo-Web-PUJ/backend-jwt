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

import com.example.backend.dto.ArcoRequestDTO;
import com.example.backend.dto.ArcoResponseDTO;
import com.example.backend.dto.EliminacionArcoResponseDTO;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.service.ArcoService;
import com.example.backend.sesion.SesionActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/procesos/{procesoId}/arcos")
@Tag(name = "Arcos", description = "HU-11 a HU-13: flujos de secuencia dentro de un mismo pool")
public class ArcoController {

    private final ArcoService arcoService;
    private final SesionActual sesionActual;

    public ArcoController(ArcoService arcoService, SesionActual sesionActual) {
        this.arcoService = arcoService;
        this.sesionActual = sesionActual;
    }

    @GetMapping
    @Operation(summary = "Listar los arcos del proceso")
    public ResponseEntity<List<ArcoResponseDTO>> listar(@PathVariable Long procesoId, HttpSession session) {
        return ResponseEntity.ok(arcoService.listarPorProceso(procesoId, sesionActual.obtener(session)));
    }

    @PostMapping
    @Operation(summary = "Crear un arco entre actividades, gateways o eventos del mismo pool")
    public ResponseEntity<ArcoResponseDTO> crear(
            @PathVariable Long procesoId,
            @Valid @RequestBody ArcoRequestDTO request,
            HttpSession session) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(arcoService.crearArco(procesoId, request, sesionActual.obtener(session)));
    }

    @PutMapping("/{arcoId}")
    @Operation(summary = "Editar un arco (nodos, etiqueta y condición)")
    public ResponseEntity<ArcoResponseDTO> editar(
            @PathVariable Long procesoId,
            @PathVariable Long arcoId,
            @Valid @RequestBody ArcoRequestDTO request,
            HttpSession session) {
        return ResponseEntity.ok(arcoService.editarArco(procesoId, arcoId, request, sesionActual.obtener(session)));
    }

    @DeleteMapping("/{arcoId}")
    @Operation(summary = "Eliminar un arco; requiere confirmar=true")
    public ResponseEntity<EliminacionArcoResponseDTO> eliminar(
            @PathVariable Long procesoId,
            @PathVariable Long arcoId,
            @RequestParam(defaultValue = "false") boolean confirmar,
            HttpSession session) {
        if (!confirmar) {
            throw new OperacionInvalidaException("Debe confirmar la eliminación del arco");
        }
        return ResponseEntity.ok(arcoService.eliminarArco(procesoId, arcoId, sesionActual.obtener(session)));
    }
}
