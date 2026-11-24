package com.example.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.LoginRequestDTO;
import com.example.backend.dto.LoginResponseDTO;
import com.example.backend.dto.TokenResponseDTO;
import com.example.backend.seguridad.JwtService;
import com.example.backend.service.AuthService;
import com.example.backend.sesion.SesionActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "HU-03: inicio de sesión con JWT (Spring Security + JJWT)")
public class AuthController {

    private final AuthService authService;
    private final SesionActual sesionActual;
    private final JwtService jwtService;

    public AuthController(AuthService authService, SesionActual sesionActual, JwtService jwtService) {
        this.authService = authService;
        this.sesionActual = sesionActual;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión: valida correo y contraseña y devuelve un JWT para el encabezado Authorization: Bearer <token>")
    public ResponseEntity<TokenResponseDTO> iniciarSesion(@Valid @RequestBody LoginRequestDTO request) {
        LoginResponseDTO usuario = authService.iniciarSesion(request);
        String token = jwtService.generar(usuario.getUsuarioId());
        return ResponseEntity.ok(new TokenResponseDTO(token, "Bearer", jwtService.getExpiracionSegundos(), usuario));
    }

    @PostMapping("/token")
    @Operation(summary = "Igual que /login: devuelve un JWT (se conserva por compatibilidad)")
    public ResponseEntity<TokenResponseDTO> generarToken(@Valid @RequestBody LoginRequestDTO request) {
        return iniciarSesion(request);
    }

    @GetMapping("/sesion")
    @Operation(summary = "Consultar el usuario dueño del token (rol y empresa vigentes)")
    public ResponseEntity<LoginResponseDTO> consultarSesion() {
        return ResponseEntity.ok(sesionActual.obtenerDatosUsuario());
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión: la API no guarda estado, el cliente descarta su token")
    public ResponseEntity<Void> cerrarSesion() {
        return ResponseEntity.noContent().build();
    }
}
