package com.example.backend.sesion;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.example.backend.dto.LoginResponseDTO;
import com.example.backend.exception.SesionNoAutenticadaException;
import com.example.backend.service.AuthService;

/**
 * Único punto para que los controladores sepan quién hace la petición.
 *
 * Lee el usuario autenticado del SecurityContext de Spring Security, que
 * JwtAuthenticationFilter llenó a partir del token. El rol y la empresa ya
 * vienen de la base de datos, leídos en esa misma petición.
 */
@Component
public class SesionActual {

    private final AuthService authService;

    public SesionActual(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Usuario autenticado de la petición.
     *
     * @throws SesionNoAutenticadaException si la petición no trae un token válido
     */
    public UsuarioSesion obtener() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioSesion usuario) {
            return usuario;
        }
        throw new SesionNoAutenticadaException("Debe iniciar sesión para realizar esta operación");
    }

    /** Datos completos del usuario autenticado (nombre, correo, rol y empresa). */
    public LoginResponseDTO obtenerDatosUsuario() {
        return authService.obtenerUsuarioActivo(obtener().usuarioId());
    }
}
