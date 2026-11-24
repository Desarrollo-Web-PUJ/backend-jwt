package com.example.backend.seguridad;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.backend.dto.LoginResponseDTO;
import com.example.backend.exception.SesionNoAutenticadaException;
import com.example.backend.service.AuthService;
import com.example.backend.sesion.UsuarioSesion;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro de Spring Security que autentica cada petición con el JWT.
 *
 * 1. Lee el encabezado "Authorization: Bearer <token>".
 * 2. Valida firma y vencimiento con JwtService (librería JJWT).
 * 3. Carga el usuario desde la base de datos, así el rol y la empresa siempre
 *    están vigentes (un usuario desactivado deja de entrar de inmediato).
 * 4. Registra la autenticación en el SecurityContext con su rol como
 *    autoridad (ROLE_ADMINISTRADOR, ROLE_EDITOR o ROLE_LECTURA).
 *
 * Si no hay token, deja seguir la petición: SecurityConfig decide si la ruta
 * es pública o responde 401. Si el token es inválido o venció, responde 401.
 *
 * No es un @Component a propósito: así Spring Boot no lo registra también como
 * filtro del servidor, y solo corre dentro de la cadena de Spring Security.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO_BEARER = "Bearer ";

    /** Rutas de inicio de sesión y registro: no se revisa token (uno vencido no debe impedir entrar). */
    private static final Set<String> RUTAS_SIN_TOKEN = Set.of(
            "/api/auth/login", "/api/auth/token", "/api/empresas/registro");

    private final JwtService jwtService;
    private final AuthService authService;
    private final RespuestaErrorSeguridad respuestaError;

    public JwtAuthenticationFilter(JwtService jwtService, AuthService authService,
            RespuestaErrorSeguridad respuestaError) {
        this.jwtService = jwtService;
        this.authService = authService;
        this.respuestaError = respuestaError;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return RUTAS_SIN_TOKEN.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String encabezado = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (encabezado == null || !encabezado.startsWith(PREFIJO_BEARER)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = encabezado.substring(PREFIJO_BEARER.length()).trim();
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Long usuarioId = jwtService.validar(token);
            LoginResponseDTO usuario = authService.obtenerUsuarioActivo(usuarioId);

            UsuarioSesion principal = new UsuarioSesion(
                    usuario.getUsuarioId(), usuario.getNombre(), usuario.getEmpresaId(), usuario.getRol());
            UsernamePasswordAuthenticationToken autenticacion = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name())));

            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(autenticacion);
            SecurityContextHolder.setContext(contexto);
        } catch (SesionNoAutenticadaException e) {
            SecurityContextHolder.clearContext();
            respuestaError.escribir(response, HttpStatus.UNAUTHORIZED.value(), e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }
}
