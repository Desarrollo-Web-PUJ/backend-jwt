package com.example.web.cliente;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.example.web.modelo.Historial;
import com.example.web.modelo.Pagina;
import com.example.web.modelo.ProcesoDetalle;
import com.example.web.modelo.ProcesoFormulario;
import com.example.web.modelo.ProcesoResumen;
import com.example.web.modelo.TokenRespuesta;

/**
 * Cliente de la API REST del backend.
 *
 * Todas las peticiones autenticadas envian el JWT en el encabezado
 * "Authorization: Bearer <token>". Los errores de la API se traducen a
 * SesionExpiradaException (401) o ApiException (resto), con el mensaje que
 * devuelve el backend.
 */
@Component
public class ApiCliente {

    private static final Pattern MENSAJE = Pattern.compile("\"mensaje\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final RestClient api;

    public ApiCliente(RestClient api) {
        this.api = api;
    }

    public TokenRespuesta iniciarSesion(String correo, String password) {
        return llamar(() -> api.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("correo", correo, "password", password))
                .retrieve()
                .body(TokenRespuesta.class), false);
    }

    public Pagina<ProcesoResumen> listarProcesos(String token, String nombre, int pagina) {
        return llamar(() -> api.get()
                .uri(uri -> uri.path("/api/procesos")
                        .queryParam("page", pagina)
                        .queryParam("size", 10)
                        .queryParam("sort", "nombre")
                        .queryParamIfPresent("nombre", java.util.Optional.ofNullable(nombre).filter(n -> !n.isBlank()))
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .body(new ParameterizedTypeReference<Pagina<ProcesoResumen>>() { }), true);
    }

    public ProcesoDetalle obtenerProceso(String token, Long id) {
        return llamar(() -> api.get()
                .uri("/api/procesos/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .body(ProcesoDetalle.class), true);
    }

    public List<Historial> obtenerHistorial(String token, Long id) {
        return llamar(() -> api.get()
                .uri("/api/procesos/{id}/historial", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Historial>>() { }), true);
    }

    public ProcesoResumen crearProceso(String token, ProcesoFormulario formulario) {
        return llamar(() -> api.post()
                .uri("/api/procesos")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "nombre", formulario.getNombre().trim(),
                        "descripcion", formulario.getDescripcion() == null ? "" : formulario.getDescripcion().trim(),
                        "categoria", formulario.getCategoria().trim()))
                .retrieve()
                .body(ProcesoResumen.class), true);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * Ejecuta la llamada y traduce los errores. Con autenticada = false (login),
     * un 401 significa credenciales incorrectas y no sesion vencida.
     */
    private <T> T llamar(Supplier<T> llamada, boolean autenticada) {
        try {
            return llamada.get();
        } catch (RestClientResponseException e) {
            int estado = e.getStatusCode().value();
            if (estado == 401 && autenticada) {
                throw new SesionExpiradaException();
            }
            throw new ApiException(estado, mensaje(e.getResponseBodyAsString(), estado));
        } catch (ResourceAccessException e) {
            throw new ApiException(503, "No fue posible conectarse con la API. Intente de nuevo en unos segundos.");
        }
    }

    private static String mensaje(String cuerpo, int estado) {
        Matcher m = MENSAJE.matcher(cuerpo == null ? "" : cuerpo);
        if (m.find()) {
            return m.group(1).replace("\\\"", "\"");
        }
        return "La API respondio con el codigo " + estado;
    }
}
