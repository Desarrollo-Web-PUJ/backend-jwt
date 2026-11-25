package com.example.backend.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.backend.exception.SesionNoAutenticadaException;

import io.jsonwebtoken.security.WeakKeyException;

/**
 * Pruebas unitarias de JwtService: no levantan Spring ni base de datos,
 * solo la clase con una clave de prueba.
 */
class JwtServiceTest {

    private static final String CLAVE = "clave-de-pruebas-unitarias-de-32-bytes-o-mas";
    private static final String OTRA_CLAVE = "otra-clave-distinta-para-firmar-tokens-falsos";

    private final JwtService jwt = new JwtService(CLAVE, 60);

    @Test
    @DisplayName("Un token generado se valida y devuelve el id del usuario")
    void generarYValidar() {
        String token = jwt.generar(42L);
        assertEquals(3, token.split("\\.").length, "Encabezado, contenido y firma");
        assertEquals(42L, jwt.validar(token));
    }

    @Test
    @DisplayName("Tokens de usuarios distintos son distintos")
    void tokensDistintosPorUsuario() {
        assertNotEquals(jwt.generar(1L), jwt.generar(2L));
    }

    @Test
    @DisplayName("La expiración se expresa en segundos")
    void expiracionEnSegundos() {
        assertEquals(3600, jwt.getExpiracionSegundos());
    }

    @Test
    @DisplayName("Un token firmado con otra clave es inválido")
    void firmaDeOtraClave() {
        String ajeno = new JwtService(OTRA_CLAVE, 60).generar(42L);
        SesionNoAutenticadaException e = assertThrows(SesionNoAutenticadaException.class, () -> jwt.validar(ajeno));
        assertEquals("Token inválido", e.getMessage());
    }

    @Test
    @DisplayName("Un token alterado es inválido")
    void tokenAlterado() {
        String token = jwt.generar(42L);
        String alterado = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
        assertThrows(SesionNoAutenticadaException.class, () -> jwt.validar(alterado));
    }

    @Test
    @DisplayName("Un token vencido se rechaza con un mensaje propio")
    void tokenVencido() {
        String vencido = new JwtService(CLAVE, -1).generar(42L);
        SesionNoAutenticadaException e = assertThrows(SesionNoAutenticadaException.class, () -> jwt.validar(vencido));
        assertEquals("El token venció, inicie sesión de nuevo", e.getMessage());
    }

    @Test
    @DisplayName("Texto que no es un JWT o vacío es inválido")
    void textoInvalido() {
        assertThrows(SesionNoAutenticadaException.class, () -> jwt.validar("abc.def.ghi"));
        assertThrows(SesionNoAutenticadaException.class, () -> jwt.validar(""));
    }

    @Test
    @DisplayName("Una clave de menos de 256 bits no se acepta")
    void claveCorta() {
        assertThrows(WeakKeyException.class, () -> new JwtService("corta", 60));
    }
}
