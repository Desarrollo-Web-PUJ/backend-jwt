package com.example.backend.seguridad;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.backend.exception.SesionNoAutenticadaException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Genera y valida tokens JWT con la librería JJWT (io.jsonwebtoken).
 *
 * La librería se encarga de armar el token (encabezado.contenido.firma),
 * firmarlo con HMAC-SHA256 y, al validarlo, comprobar la firma y el
 * vencimiento. El token guarda el id del usuario (sub), la emisión (iat) y el
 * vencimiento (exp). El rol y la empresa NO van en el token: se leen de la
 * base de datos en cada petición, así un cambio de rol aplica de inmediato.
 */
@Service
public class JwtService {

    private final SecretKey clave;
    private final long expiracionSegundos;

    public JwtService(
            @Value("${jwt.secret}") String secreto,
            @Value("${jwt.expiracion-minutos}") long expiracionMinutos) {
        // HS256 exige una clave de al menos 256 bits (32 bytes)
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.expiracionSegundos = expiracionMinutos * 60;
    }

    public long getExpiracionSegundos() {
        return expiracionSegundos;
    }

    /** Crea un token firmado para el usuario indicado. */
    public String generar(Long usuarioId) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(expiracionSegundos)))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Valida la firma y el vencimiento, y devuelve el id del usuario.
     *
     * @throws SesionNoAutenticadaException si el token es inválido o venció
     */
    public Long validar(String token) {
        try {
            Claims contenido = Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(contenido.getSubject());
        } catch (ExpiredJwtException e) {
            throw new SesionNoAutenticadaException("El token venció, inicie sesión de nuevo");
        } catch (JwtException | IllegalArgumentException e) {
            throw new SesionNoAutenticadaException("Token inválido");
        }
    }
}
