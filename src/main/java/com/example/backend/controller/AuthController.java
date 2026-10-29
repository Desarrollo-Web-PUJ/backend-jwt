package com.example.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.LoginRequestDTO;
import com.example.backend.dto.LoginResponseDTO;
import com.example.backend.service.AuthService;
import com.example.backend.sesion.SesionActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "HU-03: inicio y cierre de sesión")
public class AuthController {

    private final AuthService authService;
    private final SesionActual sesionActual;

    public AuthController(AuthService authService, SesionActual sesionActual) {
        this.authService = authService;
        this.sesionActual = sesionActual;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión con correo y contraseña")
    public ResponseEntity<LoginResponseDTO> iniciarSesion(
            @Valid @RequestBody LoginRequestDTO request,
            HttpServletRequest httpRequest) {

        LoginResponseDTO usuario = authService.iniciarSesion(request);

        // Se descarta cualquier sesión previa para evitar fijación de sesión
        HttpSession anterior = httpRequest.getSession(false);
        if (anterior != null) {
            anterior.invalidate();
        }
        sesionActual.iniciar(httpRequest.getSession(true), usuario);

        return ResponseEntity.ok(usuario);
    }

    @GetMapping("/sesion")
    @Operation(summary = "Consultar el usuario de la sesión actual (rol y empresa vigentes)")
    public ResponseEntity<LoginResponseDTO> consultarSesion(HttpSession session) {
        return ResponseEntity.ok(sesionActual.obtenerDatosUsuario(session));
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión")
    public ResponseEntity<Void> cerrarSesion(HttpSession session) {
        sesionActual.cerrar(session);
        return ResponseEntity.noContent().build();
    }
}
