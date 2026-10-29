package com.example.backend.controller;

import org.springframework.data.domain.Pageable;
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

import com.example.backend.dto.EliminacionRolProcesoResponseDTO;
import com.example.backend.dto.PaginaResponseDTO;
import com.example.backend.dto.RolProcesoListItemDTO;
import com.example.backend.dto.RolProcesoRequestDTO;
import com.example.backend.dto.RolProcesoResponseDTO;
import com.example.backend.exception.OperacionInvalidaException;
import com.example.backend.service.RolProcesoService;
import com.example.backend.sesion.SesionActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/roles-proceso")
@Tag(name = "Roles de proceso", description = "HU-17 a HU-20: catálogo de roles que nombran las lanes")
public class RolProcesoController {

    private final RolProcesoService rolProcesoService;
    private final SesionActual sesionActual;

    public RolProcesoController(RolProcesoService rolProcesoService, SesionActual sesionActual) {
        this.rolProcesoService = rolProcesoService;
        this.sesionActual = sesionActual;
    }

    @GetMapping
    @Operation(summary = "Consultar roles de proceso (paginado, con búsqueda y uso en procesos)")
    public ResponseEntity<PaginaResponseDTO<RolProcesoListItemDTO>> consultar(
            @RequestParam(required = false) String nombre,
            @PageableDefault(size = 10, sort = "nombre") Pageable pageable,
            HttpSession session) {
        return ResponseEntity.ok(rolProcesoService.consultar(nombre, pageable, sesionActual.obtener(session)));
    }

    @PostMapping
    @Operation(summary = "Crear un rol de proceso")
    public ResponseEntity<RolProcesoResponseDTO> crear(
            @Valid @RequestBody RolProcesoRequestDTO request,
            HttpSession session) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rolProcesoService.crear(request, sesionActual.obtener(session)));
    }

    @PutMapping("/{rolProcesoId}")
    @Operation(summary = "Editar un rol de proceso (renombra todas las lanes que lo usan)")
    public ResponseEntity<RolProcesoResponseDTO> editar(
            @PathVariable Long rolProcesoId,
            @Valid @RequestBody RolProcesoRequestDTO request,
            HttpSession session) {
        return ResponseEntity.ok(rolProcesoService.editar(rolProcesoId, request, sesionActual.obtener(session)));
    }

    @DeleteMapping("/{rolProcesoId}")
    @Operation(summary = "Eliminar (lógicamente) un rol de proceso que no esté en uso; requiere confirmar=true")
    public ResponseEntity<EliminacionRolProcesoResponseDTO> eliminar(
            @PathVariable Long rolProcesoId,
            @RequestParam(defaultValue = "false") boolean confirmar,
            HttpSession session) {
        if (!confirmar) {
            throw new OperacionInvalidaException("Debe confirmar la eliminación del rol de proceso");
        }
        return ResponseEntity.ok(rolProcesoService.eliminar(rolProcesoId, sesionActual.obtener(session)));
    }
}
