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

import com.example.backend.dto.ActividadDTO;
import com.example.backend.dto.ActividadRequestDTO;
import com.example.backend.dto.EliminacionActividadResponseDTO;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.service.ActividadService;
import com.example.backend.sesion.SesionActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/procesos/{procesoId}/actividades")
@Tag(name = "Actividades", description = "HU-08 a HU-10: tareas del proceso")
public class ActividadController {

    private final ActividadService actividadService;
    private final SesionActual sesionActual;

    public ActividadController(ActividadService actividadService, SesionActual sesionActual) {
        this.actividadService = actividadService;
        this.sesionActual = sesionActual;
    }

    @GetMapping
    @Operation(summary = "Listar las actividades del proceso")
    public ResponseEntity<List<ActividadDTO>> listar(@PathVariable Long procesoId, HttpSession session) {
        return ResponseEntity.ok(actividadService.listarPorProceso(procesoId, sesionActual.obtener(session)));
    }

    @PostMapping
    @Operation(summary = "Crear una actividad en una lane")
    public ResponseEntity<ActividadDTO> crear(
            @PathVariable Long procesoId,
            @Valid @RequestBody ActividadRequestDTO request,
            HttpSession session) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(actividadService.crearActividad(procesoId, request, sesionActual.obtener(session)));
    }

    @PutMapping("/{actividadId}")
    @Operation(summary = "Editar una actividad (nombre, tipo, lane responsable y posición)")
    public ResponseEntity<ActividadDTO> editar(
            @PathVariable Long procesoId,
            @PathVariable Long actividadId,
            @Valid @RequestBody ActividadRequestDTO request,
            HttpSession session) {
        return ResponseEntity.ok(
                actividadService.editarActividad(procesoId, actividadId, request, sesionActual.obtener(session)));
    }

    @DeleteMapping("/{actividadId}")
    @Operation(summary = "Eliminar una actividad y sus arcos; requiere confirmar=true")
    public ResponseEntity<EliminacionActividadResponseDTO> eliminar(
            @PathVariable Long procesoId,
            @PathVariable Long actividadId,
            @RequestParam(defaultValue = "false") boolean confirmar,
            HttpSession session) {
        if (!confirmar) {
            throw new OperacionInvalidaException("Debe confirmar la eliminación de la actividad");
        }
        return ResponseEntity.ok(
                actividadService.eliminarActividad(procesoId, actividadId, sesionActual.obtener(session)));
    }
}
