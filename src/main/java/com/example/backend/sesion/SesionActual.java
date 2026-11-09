package com.example.backend.sesion;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.backend.dto.LoginResponseDTO;
import com.example.backend.exception.SesionNoAutenticadaException;
import com.example.backend.seguridad.JwtService;
import com.example.backend.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Único punto para saber quién hace la petición.
 *
 * Acepta dos formas de autenticación:
 * 1. JWT: encabezado "Authorization: Bearer <token>" (tiene prioridad).
 * 2. Sesión HTTP (cookie JSESSIONID), la que usa el frontend actual.
 *
 * En ambos casos solo se obtiene el id del usuario; la empresa y el rol se leen
 * de la base de datos en cada petición, así un cambio de rol aplica de inmediato.
 */
@Component
public class SesionActual {

    private static final String ATRIBUTO_USUARIO_ID = "usuarioId";
    private static final String PREFIJO_BEARER = "Bearer ";

    private final AuthService authService;
    private final JwtService jwtService;

    public SesionActual(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    public void iniciar(HttpSession session, LoginResponseDTO usuario) {
        session.setAttribute(ATRIBUTO_USUARIO_ID, usuario.getUsuarioId());
    }

    public void cerrar(HttpSession session) {
        session.invalidate();
    }

    /**
     * Usuario autenticado de la petición.
     *
     * @throws SesionNoAutenticadaException si no hay token ni sesión válidos
     */
    public UsuarioSesion obtener(HttpSession session) {
        LoginResponseDTO usuario = obtenerDatosUsuario(session);
        return new UsuarioSesion(
                usuario.getUsuarioId(),
                usuario.getNombre(),
                usuario.getEmpresaId(),
                usuario.getRol());
    }

    /** Datos completos del usuario autenticado (nombre, correo, rol y empresa). */
    public LoginResponseDTO obtenerDatosUsuario(HttpSession session) {
        String token = tokenDeLaPeticion();
        if (token != null) {
            return authService.obtenerUsuarioActivo(jwtService.validar(token));
        }

        Object usuarioId = session.getAttribute(ATRIBUTO_USUARIO_ID);
        if (!(usuarioId instanceof Long id)) {
            throw new SesionNoAutenticadaException("Debe iniciar sesión para realizar esta operación");
        }
        return authService.obtenerUsuarioActivo(id);
    }

    /** Token del encabezado Authorization, o null si la petición no trae uno. */
    private String tokenDeLaPeticion() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos)) {
            return null;
        }
        HttpServletRequest request = atributos.getRequest();
        String encabezado = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (encabezado == null || !encabezado.startsWith(PREFIJO_BEARER)) {
            return null;
        }
        String token = encabezado.substring(PREFIJO_BEARER.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
