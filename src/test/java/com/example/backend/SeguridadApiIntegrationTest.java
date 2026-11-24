package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Pruebas de integración de la API REST: levantan la aplicación completa en un
 * puerto aleatorio (con Spring Security, controladores, servicios y una base H2
 * en memoria) y la consumen por HTTP como lo haría el frontend.
 *
 * Cubren los casos de uso principales y los escenarios de seguridad:
 * autenticación con JWT (401), autorización por rol (403) y aislamiento de la
 * información entre empresas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeguridadApiIntegrationTest {

    private static final String CLAVE = "Clave12345";

    @Value("${local.server.port}")
    private int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    /** Una empresa nueva con su administrador, distinta en cada prueba. */
    private Empresa empresaA;

    @BeforeEach
    void crearEmpresa() throws Exception {
        empresaA = registrarEmpresa();
    }

    // ------------------------------------------------------------------ Autenticación

    @Test
    @DisplayName("Registro de empresa es público y responde 201")
    void registroDeEmpresaEsPublico() {
        assertTrue(empresaA.id() > 0);
    }

    @Test
    @DisplayName("Login con contraseña incorrecta responde 401")
    void loginConClaveIncorrecta() throws Exception {
        HttpResponse<String> r = post("/api/auth/login", null, login(empresaA.correoAdmin(), "incorrecta"));
        assertEquals(401, r.statusCode());
        assertTrue(r.body().contains("Correo o contraseña incorrectos"));
    }

    @Test
    @DisplayName("Login correcto devuelve un JWT de tipo Bearer")
    void loginCorrectoDevuelveToken() throws Exception {
        HttpResponse<String> r = post("/api/auth/login", null, login(empresaA.correoAdmin(), CLAVE));
        assertEquals(200, r.statusCode());
        String token = texto(r.body(), "token");
        assertEquals(3, token.split("\\.").length, "El token debe tener encabezado, contenido y firma");
        assertTrue(r.body().contains("\"tipo\":\"Bearer\""));
        assertTrue(r.body().contains("\"rol\":\"ADMINISTRADOR\""));
    }

    @Test
    @DisplayName("Sin token, un recurso protegido responde 401")
    void sinTokenResponde401() throws Exception {
        assertEquals(401, get("/api/procesos", null).statusCode());
    }

    @Test
    @DisplayName("Con un token inventado responde 401")
    void tokenFalsoResponde401() throws Exception {
        HttpResponse<String> r = get("/api/procesos", "abc.def.ghi");
        assertEquals(401, r.statusCode());
        assertTrue(r.body().contains("Token inválido"));
    }

    @Test
    @DisplayName("Con un token alterado responde 401")
    void tokenAlteradoResponde401() throws Exception {
        String token = empresaA.tokenAdmin();
        String alterado = token.substring(0, token.length() - 3) + (token.endsWith("aaa") ? "bbb" : "aaa");
        assertEquals(401, get("/api/procesos", alterado).statusCode());
    }

    @Test
    @DisplayName("Con token válido, GET /api/auth/sesion devuelve el usuario dueño del token")
    void sesionDevuelveUsuarioDelToken() throws Exception {
        HttpResponse<String> r = get("/api/auth/sesion", empresaA.tokenAdmin());
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains(empresaA.correoAdmin()));
    }

    @Test
    @DisplayName("Swagger es público")
    void swaggerEsPublico() throws Exception {
        assertEquals(200, get("/v3/api-docs", null).statusCode());
    }

    // ------------------------------------------------------------------ Casos de uso principales

    @Test
    @DisplayName("El administrador crea un proceso y lo ve en el listado y en el detalle")
    void administradorCreaYConsultaProceso() throws Exception {
        long procesoId = crearProceso(empresaA.tokenAdmin(), "Solicitud de vacaciones");

        HttpResponse<String> listado = get("/api/procesos?page=0&size=10", empresaA.tokenAdmin());
        assertEquals(200, listado.statusCode());
        assertTrue(listado.body().contains("Solicitud de vacaciones"));

        HttpResponse<String> detalle = get("/api/procesos/" + procesoId, empresaA.tokenAdmin());
        assertEquals(200, detalle.statusCode());
        assertTrue(detalle.body().contains("\"pools\""), "El detalle trae el diagrama completo");
    }

    @Test
    @DisplayName("El historial registra quién creó el proceso")
    void historialRegistraAutor() throws Exception {
        long procesoId = crearProceso(empresaA.tokenAdmin(), "Compras");
        HttpResponse<String> r = get("/api/procesos/" + procesoId + "/historial", empresaA.tokenAdmin());
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("Admin de prueba"));
    }

    // ------------------------------------------------------------------ Autorización por rol

    @Test
    @DisplayName("Un EDITOR puede crear procesos")
    void editorPuedeCrearProceso() throws Exception {
        String tokenEditor = crearUsuario(empresaA, "EDITOR");
        crearProceso(tokenEditor, "Proceso del editor");
    }

    @Test
    @DisplayName("Un usuario LECTURA no puede crear procesos (403)")
    void lecturaNoPuedeCrearProceso() throws Exception {
        String tokenLectura = crearUsuario(empresaA, "LECTURA");
        HttpResponse<String> r = post("/api/procesos", tokenLectura, proceso("No permitido"));
        assertEquals(403, r.statusCode());
    }

    @Test
    @DisplayName("Un usuario LECTURA sí puede consultar los procesos de su empresa")
    void lecturaPuedeConsultar() throws Exception {
        crearProceso(empresaA.tokenAdmin(), "Visible para lectura");
        String tokenLectura = crearUsuario(empresaA, "LECTURA");
        HttpResponse<String> r = get("/api/procesos", tokenLectura);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("Visible para lectura"));
    }

    @Test
    @DisplayName("Un EDITOR no puede gestionar usuarios ni eliminar procesos (403)")
    void editorNoPuedeAdministrar() throws Exception {
        String tokenEditor = crearUsuario(empresaA, "EDITOR");
        assertEquals(403, get("/api/empresas/" + empresaA.id() + "/usuarios", tokenEditor).statusCode());

        long procesoId = crearProceso(empresaA.tokenAdmin(), "Proceso protegido");
        assertEquals(403, delete("/api/procesos/" + procesoId + "?confirmar=true", tokenEditor).statusCode());
    }

    // ------------------------------------------------------------------ Aislamiento entre empresas

    @Test
    @DisplayName("Una empresa no ve los procesos de otra (ni en el listado ni por id)")
    void aislamientoEntreEmpresas() throws Exception {
        long procesoDeA = crearProceso(empresaA.tokenAdmin(), "Proceso privado de A");
        Empresa empresaB = registrarEmpresa();

        HttpResponse<String> listadoB = get("/api/procesos", empresaB.tokenAdmin());
        assertEquals(200, listadoB.statusCode());
        assertFalse(listadoB.body().contains("Proceso privado de A"));

        assertEquals(404, get("/api/procesos/" + procesoDeA, empresaB.tokenAdmin()).statusCode());
    }

    @Test
    @DisplayName("Una empresa no puede gestionar los usuarios de otra (403)")
    void noGestionaUsuariosDeOtraEmpresa() throws Exception {
        Empresa empresaB = registrarEmpresa();
        HttpResponse<String> r = get("/api/empresas/" + empresaA.id() + "/usuarios", empresaB.tokenAdmin());
        assertEquals(403, r.statusCode());
    }

    @Test
    @DisplayName("Un proceso compartido se puede ver pero no editar desde la otra empresa")
    void procesoCompartidoEsSoloLectura() throws Exception {
        long procesoDeA = crearProceso(empresaA.tokenAdmin(), "Proceso compartido");
        Empresa empresaB = registrarEmpresa();

        HttpResponse<String> compartir = post("/api/procesos/" + procesoDeA + "/comparticiones",
                empresaA.tokenAdmin(), "{\"empresaDestinoId\":" + empresaB.id() + "}");
        assertEquals(201, compartir.statusCode(), compartir.body());

        assertEquals(200, get("/api/procesos/" + procesoDeA, empresaB.tokenAdmin()).statusCode());

        HttpResponse<String> editar = put("/api/procesos/" + procesoDeA, empresaB.tokenAdmin(),
                "{\"nombre\":\"Intento\",\"descripcion\":\"\",\"categoria\":\"X\",\"estado\":\"BORRADOR\"}");
        assertTrue(editar.statusCode() == 403 || editar.statusCode() == 404,
                "Editar un proceso ajeno debe rechazarse, llegó " + editar.statusCode());
    }

    // ------------------------------------------------------------------ Utilidades

    private record Empresa(long id, String correoAdmin, String tokenAdmin) {
    }

    private Empresa registrarEmpresa() throws Exception {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String correo = "admin" + sufijo + "@prueba.com";
        HttpResponse<String> r = post("/api/empresas/registro", null, """
                {"nombre":"Empresa %s","nit":"NIT-%s","correoContacto":"contacto%s@prueba.com",
                 "adminNombre":"Admin de prueba","adminCorreo":"%s","adminPassword":"%s"}
                """.formatted(sufijo, sufijo, sufijo, correo, CLAVE));
        assertEquals(201, r.statusCode(), r.body());
        return new Empresa(numero(r.body(), "id"), correo, token(correo));
    }

    private String crearUsuario(Empresa empresa, String rol) throws Exception {
        String correo = rol.toLowerCase() + UUID.randomUUID().toString().substring(0, 8) + "@prueba.com";
        HttpResponse<String> r = post("/api/empresas/" + empresa.id() + "/usuarios/registro", empresa.tokenAdmin(),
                """
                {"nombre":"Usuario %s","correo":"%s","password":"%s","rol":"%s"}
                """.formatted(rol, correo, CLAVE, rol));
        assertEquals(201, r.statusCode(), r.body());
        return token(correo);
    }

    private long crearProceso(String token, String nombre) throws Exception {
        HttpResponse<String> r = post("/api/procesos", token, proceso(nombre));
        assertEquals(201, r.statusCode(), r.body());
        return numero(r.body(), "id");
    }

    private String token(String correo) throws Exception {
        HttpResponse<String> r = post("/api/auth/login", null, login(correo, CLAVE));
        assertEquals(200, r.statusCode(), r.body());
        return texto(r.body(), "token");
    }

    private static String login(String correo, String clave) {
        return "{\"correo\":\"%s\",\"password\":\"%s\"}".formatted(correo, clave);
    }

    private static String proceso(String nombre) {
        return "{\"nombre\":\"%s\",\"descripcion\":\"Prueba\",\"categoria\":\"Pruebas\"}".formatted(nombre);
    }

    private HttpResponse<String> get(String ruta, String token) throws IOException, InterruptedException {
        return enviar(peticion(ruta, token).GET());
    }

    private HttpResponse<String> delete(String ruta, String token) throws IOException, InterruptedException {
        return enviar(peticion(ruta, token).DELETE());
    }

    private HttpResponse<String> post(String ruta, String token, String json) throws IOException, InterruptedException {
        return enviar(peticion(ruta, token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    private HttpResponse<String> put(String ruta, String token, String json) throws IOException, InterruptedException {
        return enviar(peticion(ruta, token).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)));
    }

    private HttpRequest.Builder peticion(String ruta, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return b;
    }

    private HttpResponse<String> enviar(HttpRequest.Builder b) throws IOException, InterruptedException {
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String texto(String json, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        assertTrue(m.find(), "No se encontró " + campo + " en " + json);
        return m.group(1);
    }

    private static long numero(String json, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\"\\s*:\\s*(\\d+)").matcher(json);
        assertTrue(m.find(), "No se encontró " + campo + " en " + json);
        return Long.parseLong(m.group(1));
    }
}
