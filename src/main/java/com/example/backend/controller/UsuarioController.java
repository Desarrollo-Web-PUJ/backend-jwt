package com.example.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.UsuarioActualizarRolRequestDTO;
import com.example.backend.dto.UsuarioRegistroRequestDTO;
import com.example.backend.dto.UsuarioResponseDTO;
import com.example.backend.exception.PermisoDenegadoException;
import com.example.backend.service.UsuarioService;
import com.example.backend.sesion.SesionActual;
import com.example.backend.sesion.UsuarioSesion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/empresas/{empresaId}/usuarios")
@Tag(name = "Usuarios", description = "HU-02: colaboradores de la empresa y sus roles de acceso")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final SesionActual sesionActual;

    public UsuarioController(UsuarioService usuarioService, SesionActual sesionActual) {
        this.usuarioService = usuarioService;
        this.sesionActual = sesionActual;
    }

    @PostMapping("/registro")
    @Operation(summary = "Registrar un colaborador en la empresa")
    public ResponseEntity<UsuarioResponseDTO> registrarUsuario(
            @PathVariable Long empresaId,
            @Valid @RequestBody UsuarioRegistroRequestDTO request,
            HttpSession session) {
        UsuarioSesion sesion = sesionDeLaEmpresa(empresaId, session);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.registrarUsuario(sesion, request));
    }

    @GetMapping
    @Operation(summary = "Listar los usuarios de la empresa")
    public ResponseEntity<List<UsuarioResponseDTO>> listarUsuarios(
            @PathVariable Long empresaId,
            HttpSession session) {
        return ResponseEntity.ok(usuarioService.listarUsuarios(sesionDeLaEmpresa(empresaId, session)));
    }

    @PutMapping("/{usuarioId}/rol")
    @Operation(summary = "Cambiar el rol de acceso de un colaborador")
    public ResponseEntity<UsuarioResponseDTO> cambiarRol(
            @PathVariable Long empresaId,
            @PathVariable Long usuarioId,
            @Valid @RequestBody UsuarioActualizarRolRequestDTO request,
            HttpSession session) {
        UsuarioSesion sesion = sesionDeLaEmpresa(empresaId, session);
        return ResponseEntity.ok(usuarioService.cambiarRol(sesion, usuarioId, request));
    }

    @PatchMapping("/{usuarioId}/desactivar")
    @Operation(summary = "Desactivar un colaborador (sin borrar su historial)")
    public ResponseEntity<UsuarioResponseDTO> desactivarUsuario(
            @PathVariable Long empresaId,
            @PathVariable Long usuarioId,
            HttpSession session) {
        UsuarioSesion sesion = sesionDeLaEmpresa(empresaId, session);
        return ResponseEntity.ok(usuarioService.desactivarUsuario(sesion, usuarioId));
    }

    // La empresa de la URL debe ser la del usuario autenticado: aislamiento por empresa
    private UsuarioSesion sesionDeLaEmpresa(Long empresaId, HttpSession session) {
        UsuarioSesion sesion = sesionActual.obtener(session);
        if (!sesion.empresaId().equals(empresaId)) {
            throw new PermisoDenegadoException("No puede gestionar usuarios de otra empresa");
        }
        return sesion;
    }
}
