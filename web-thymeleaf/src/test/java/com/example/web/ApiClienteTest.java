package com.example.web;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.example.web.cliente.ApiCliente;
import com.example.web.cliente.ApiException;
import com.example.web.cliente.SesionExpiradaException;
import com.example.web.modelo.Pagina;
import com.example.web.modelo.ProcesoFormulario;
import com.example.web.modelo.ProcesoResumen;
import com.example.web.modelo.TokenRespuesta;

/** Pruebas del cliente de la API con un servidor HTTP simulado. */
class ApiClienteTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://api");
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final ApiCliente cliente = new ApiCliente(builder.build());

    @Test
    void iniciarSesionDevuelveElTokenYElUsuario() {
        servidor.expect(requestTo("http://api/api/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"token":"a.b.c","tipo":"Bearer","expiraEnSegundos":3600,
                         "usuario":{"usuarioId":1,"nombre":"Ana","correo":"ana@x.com","rol":"EDITOR","empresaId":7}}
                        """, MediaType.APPLICATION_JSON));

        TokenRespuesta respuesta = cliente.iniciarSesion("ana@x.com", "Clave12345");

        assertEquals("a.b.c", respuesta.token());
        assertEquals("EDITOR", respuesta.usuario().rol());
        servidor.verify();
    }

    @Test
    void credencialesIncorrectasMuestranElMensajeDeLaApi() {
        servidor.expect(requestTo("http://api/api/auth/login"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"mensaje\":\"Correo o contraseña incorrectos\",\"errores\":[]}"));

        ApiException error = assertThrows(ApiException.class, () -> cliente.iniciarSesion("x@x.com", "mala"));
        assertEquals(401, error.getEstado());
        assertEquals("Correo o contraseña incorrectos", error.getMessage());
    }

    @Test
    void listarProcesosEnviaElJwtEnElEncabezado() {
        servidor.expect(requestTo(startsWith("http://api/api/procesos")))
                .andExpect(header("Authorization", "Bearer a.b.c"))
                .andRespond(withSuccess("""
                        {"content":[{"id":3,"nombre":"Compras","categoria":"Finanzas","estado":"BORRADOR","activo":true}],
                         "number":0,"size":10,"totalElements":1,"totalPages":1,"first":true,"last":true}
                        """, MediaType.APPLICATION_JSON));

        Pagina<ProcesoResumen> pagina = cliente.listarProcesos("a.b.c", "", 0);

        assertEquals(1, pagina.totalElements());
        assertEquals("Compras", pagina.content().get(0).nombre());
        servidor.verify();
    }

    @Test
    void unTokenVencidoSeTraduceEnSesionExpirada() {
        servidor.expect(requestTo(startsWith("http://api/api/procesos")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(SesionExpiradaException.class, () -> cliente.listarProcesos("vencido", "", 0));
    }

    @Test
    void sinPermisoSeInformaElError403() {
        servidor.expect(requestTo("http://api/api/procesos"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"mensaje\":\"No tiene permisos para realizar esta operación\",\"errores\":[]}"));

        ProcesoFormulario formulario = new ProcesoFormulario();
        formulario.setNombre("Nuevo");
        formulario.setCategoria("General");

        ApiException error = assertThrows(ApiException.class, () -> cliente.crearProceso("a.b.c", formulario));
        assertEquals(403, error.getEstado());
    }
}
