package com.example.backend.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.backend.exception.SesionNoAutenticadaException;

/**
 * Genera y valida tokens JWT firmados con HMAC-SHA256 (HS256).
 *
 * Estructura del token: encabezado.contenido.firma (cada parte en Base64 URL).
 * El contenido guarda el id del usuario (sub), la fecha de emisión (iat) y la
 * de vencimiento (exp). El rol y la empresa NO van en el token: se leen de la
 * base de datos en cada petición, así un cambio de rol aplica de inmediato.
 */
@Service
public class JwtService {

    private static final String ENCABEZADO = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final Pattern SUB = Pattern.compile("\"sub\":\"(\\d+)\"");
    private static final Pattern EXP = Pattern.compile("\"exp\":(\\d+)");

    private final byte[] clave;
    private final long expiracionSegundos;

    public JwtService(
            @Value("${jwt.secret}") String secreto,
            @Value("${jwt.expiracion-minutos}") long expiracionMinutos) {
        if (secreto.length() < 32) {
            throw new IllegalStateException("jwt.secret debe tener al menos 32 caracteres");
        }
        this.clave = secreto.getBytes(StandardCharsets.UTF_8);
        this.expiracionSegundos = expiracionMinutos * 60;
    }

    public long getExpiracionSegundos() {
        return expiracionSegundos;
    }

    /** Crea un token para el usuario indicado. */
    public String generar(Long usuarioId) {
        long ahora = Instant.now().getEpochSecond();
        String contenido = "{\"sub\":\"" + usuarioId + "\",\"iat\":" + ahora
                + ",\"exp\":" + (ahora + expiracionSegundos) + "}";

        String sinFirma = base64(ENCABEZADO.getBytes(StandardCharsets.UTF_8)) + "."
                + base64(contenido.getBytes(StandardCharsets.UTF_8));
        return sinFirma + "." + base64(firmar(sinFirma));
    }

    /**
     * Valida la firma y el vencimiento, y devuelve el id del usuario.
     *
     * @throws SesionNoAutenticadaException si el token es inválido o venció
     */
    public Long validar(String token) {
        String[] partes = token.split("\\.");
        if (partes.length != 3) {
            throw new SesionNoAutenticadaException("Token inválido");
        }

        byte[] firmaEsperada = firmar(partes[0] + "." + partes[1]);
        byte[] firmaRecibida;
        try {
            firmaRecibida = Base64.getUrlDecoder().decode(partes[2]);
        } catch (IllegalArgumentException e) {
            throw new SesionNoAutenticadaException("Token inválido");
        }
        // Comparación en tiempo constante para no filtrar información
        if (!MessageDigest.isEqual(firmaEsperada, firmaRecibida)) {
            throw new SesionNoAutenticadaException("Token inválido: la firma no coincide");
        }

        String contenido;
        try {
            contenido = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new SesionNoAutenticadaException("Token inválido");
        }

        Matcher exp = EXP.matcher(contenido);
        if (!exp.find() || Long.parseLong(exp.group(1)) < Instant.now().getEpochSecond()) {
            throw new SesionNoAutenticadaException("El token venció, inicie sesión de nuevo");
        }

        Matcher sub = SUB.matcher(contenido);
        if (!sub.find()) {
            throw new SesionNoAutenticadaException("Token inválido");
        }
        return Long.valueOf(sub.group(1));
    }

    private byte[] firmar(String datos) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return mac.doFinal(datos.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
    }

    private static String base64(byte[] datos) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(datos);
    }
}
