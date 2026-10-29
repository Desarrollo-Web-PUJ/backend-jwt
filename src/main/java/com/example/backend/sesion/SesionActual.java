package com.example.backend.sesion;

import org.springframework.stereotype.Component;

import com.example.backend.dto.LoginResponseDTO;
import com.example.backend.exception.SesionNoAutenticadaException;
import com.example.backend.service.AuthService;

import jakarta.servlet.http.HttpSession;

/**
 * Único punto de acceso a la sesión HTTP.
 *
 * La sesión solo guarda el id del usuario. La empresa y el rol se leen de la
 * base de datos en cada petición, de modo que un cambio de rol o una
 * desactivación se aplican de inmediato y no en el siguiente inicio de sesión.
 */
@Component
public class SesionActual {

    private static final String ATRIBUTO_USUARIO_ID = "usuarioId";

    private final AuthService authService;

    public SesionActual(AuthService authService) {
        this.authService = authService;
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
     * @throws SesionNoAutenticadaException si no hay sesión o el usuario ya no está activo
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
        Object usuarioId = session.getAttribute(ATRIBUTO_USUARIO_ID);
        if (!(usuarioId instanceof Long id)) {
            throw new SesionNoAutenticadaException("Debe iniciar sesión para realizar esta operación");
        }
        return authService.obtenerUsuarioActivo(id);
    }
}
