package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prueba de integración del recorrido completo de modelado (el mismo de la
 * colección Postman): roles de proceso, pools, lanes, actividades, gateway,
 * arcos con sus reglas, evento y flujo de mensaje, historial, publicación,
 * compartición entre empresas y eliminación lógica. También cubre la gestión
 * de usuarios (cambio de rol y desactivación).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlujoDiagramaIntegrationTest {

    private static final String CLAVE = "Clave12345";

    @Value("${local.server.port}")
    private int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("Modelado completo de un proceso BPMN y su ciclo de vida")
    void modeladoCompleto() throws Exception {
        Empresa a = registrarEmpresa();
        String t = a.token();

        // Roles de proceso (HU de roles): creación, nombre repetido y listado paginado
        long rolEmpleado = id(esperar(201, post("/api/roles-proceso", t, "{\"nombre\":\"Empleado\",\"descripcion\":\"Quien solicita\"}")));
        long rolJefe = id(esperar(201, post("/api/roles-proceso", t, "{\"nombre\":\"Jefe inmediato\",\"descripcion\":null}")));
        esperar(409, post("/api/roles-proceso", t, "{\"nombre\":\"empleado\",\"descripcion\":null}"));
        assertTrue(esperar(200, get("/api/roles-proceso?page=0&size=10", t)).contains("Jefe inmediato"));

        // Proceso y validación del nombre
        long p = id(esperar(201, post("/api/procesos", t,
                "{\"nombre\":\"Solicitud de vacaciones\",\"descripcion\":\"Ejemplo\",\"categoria\":\"Talento humano\"}")));
        esperar(400, post("/api/procesos", t, "{\"nombre\":\" \",\"descripcion\":\"\",\"categoria\":\"X\"}"));
        String base = "/api/procesos/" + p;

        // Pools: el de la empresa se crea con el proceso; se agrega uno externo
        long poolPropio = id(esperar(200, get(base + "/pools", t)));
        long poolExterno = id(esperar(201, post(base + "/pools", t, "{\"nombre\":\"Servicio de correo\",\"tipo\":\"EXTERNO\"}")));
        esperar(200, get(base + "/pools/" + poolPropio + "/roles-disponibles", t));

        // Lanes, una por rol de proceso; un rol en uso no se puede eliminar
        String lanes = base + "/pools/" + poolPropio + "/lanes";
        long laneEmpleado = id(esperar(201, post(lanes, t, "{\"rolProcesoId\":" + rolEmpleado + "}")));
        long laneJefe = id(esperar(201, post(lanes, t, "{\"rolProcesoId\":" + rolJefe + "}")));
        esperar(400, delete("/api/roles-proceso/" + rolEmpleado + "?confirmar=true", t));
        esperar(200, get(lanes, t));

        // Actividades y gateway exclusivo
        long radicar = id(esperar(201, post(base + "/actividades", t, actividad("Radicar solicitud", laneEmpleado, 36))));
        long revisar = id(esperar(201, post(base + "/actividades", t, actividad("Revisar solicitud", laneJefe, 216))));
        long gateway = id(esperar(201, post(base + "/gateways", t,
                "{\"tipo\":\"EXCLUSIVO\",\"poolId\":" + poolPropio + ",\"posicionX\":400,\"posicionY\":195}")));

        // Arcos y sus reglas
        long arco = id(esperar(201, post(base + "/arcos", t, arco("ACTIVIDAD", radicar, "ACTIVIDAD", revisar, null))));
        esperar(201, post(base + "/arcos", t, arco("ACTIVIDAD", revisar, "GATEWAY", gateway, null)));
        esperar(409, post(base + "/arcos", t, arco("ACTIVIDAD", radicar, "ACTIVIDAD", revisar, null)));
        esperar(400, post(base + "/arcos", t, arco("GATEWAY", gateway, "ACTIVIDAD", radicar, null)));
        esperar(201, post(base + "/arcos", t, arco("GATEWAY", gateway, "ACTIVIDAD", radicar, "\"dias > 15\"")));
        esperar(400, delete(base + "/arcos/" + arco, t));

        // Arrastrar y soltar: la nueva posición se guarda con PUT
        esperar(200, put(base + "/actividades/" + revisar, t, actividad("Revisar solicitud", laneJefe, 515)));

        // Evento de mensaje hacia el pool externo y su flujo de mensaje
        long evento = id(esperar(201, post(base + "/eventos-mensaje", t, """
                {"tipo":"THROW","poolId":%d,"laneId":%d,"nombreMensaje":"NotificarAprobacion",
                 "claveCorrelacion":"numero de solicitud","comportamientoSinCorrelacion":null,"origenExterno":false,
                 "tipoDestinoExterno":"CORREO","comportamientoFallo":"CONTINUAR",
                 "campos":[{"nombre":"numeroSolicitud","tipoDato":"Texto"}],"actividadesConsumidoras":[],
                 "posicionX":700,"posicionY":null}
                """.formatted(poolPropio, laneJefe))));
        esperar(201, post(base + "/flujos-mensaje", t,
                "{\"origenId\":" + evento + ",\"destinoId\":null,\"poolDestinoId\":" + poolExterno + ",\"etiqueta\":null}"));

        // Consultas de cada tipo de elemento y del diagrama completo
        for (String recurso : new String[] {"actividades", "gateways", "arcos", "eventos-mensaje", "flujos-mensaje"}) {
            esperar(200, get(base + "/" + recurso, t));
        }
        String detalle = esperar(200, get(base, t));
        assertTrue(detalle.contains("Radicar solicitud") && detalle.contains("dias > 15"), detalle);
        assertTrue(esperar(200, get(base + "/historial", t)).contains("gateway"));

        // Publicación
        assertTrue(esperar(200, put(base, t,
                "{\"nombre\":\"Solicitud de vacaciones\",\"descripcion\":\"Ejemplo\",\"categoria\":\"Talento humano\",\"estado\":\"PUBLICADO\"}"))
                .contains("PUBLICADO"));

        // Compartir con otra empresa en solo lectura y dejar de compartir
        Empresa b = registrarEmpresa();
        esperar(201, post(base + "/comparticiones", t, "{\"empresaDestinoId\":" + b.id() + "}"));
        esperar(200, get(base + "/comparticiones", t));
        assertTrue(esperar(200, get("/api/procesos/compartidos-conmigo", b.token())).contains("Solicitud de vacaciones"));
        esperar(200, get(base, b.token()));
        esperar(204, delete(base + "/comparticiones/" + b.id(), t));

        // Eliminación lógica: exige confirmación
        esperar(400, delete(base, t));
        assertEquals(2, delete(base + "?confirmar=true", t).statusCode() / 100);
    }

    @Test
    @DisplayName("El administrador cambia el rol de un colaborador y lo desactiva")
    void gestionDeUsuarios() throws Exception {
        Empresa a = registrarEmpresa();
        String usuarios = "/api/empresas/" + a.id() + "/usuarios";
        String correo = "editor" + UUID.randomUUID().toString().substring(0, 8) + "@prueba.com";
        long usuario = id(esperar(201, post(usuarios + "/registro", a.token(),
                "{\"nombre\":\"Eva Editora\",\"correo\":\"%s\",\"password\":\"%s\",\"rol\":\"EDITOR\"}".formatted(correo, CLAVE))));
        assertTrue(esperar(200, get(usuarios, a.token())).contains(correo));

        assertTrue(esperar(200, put(usuarios + "/" + usuario + "/rol", a.token(), "{\"rol\":\"LECTURA\"}")).contains("LECTURA"));
        esperar(200, patch(usuarios + "/" + usuario + "/desactivar", a.token()));

        // Un usuario desactivado ya no puede iniciar sesión
        assertEquals(401, post("/api/auth/login", null, login(correo, CLAVE)).statusCode());
        esperar(204, post("/api/auth/logout", a.token(), ""));
    }

    // ------------------------------------------------------------------ apoyo

    private record Empresa(long id, String token) {
    }

    private Empresa registrarEmpresa() throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8);
        String correo = "admin" + s + "@flujo.com";
        String r = esperar(201, post("/api/empresas/registro", null, """
                {"nombre":"Flujo %s","nit":"FLU-%s","correoContacto":"c%s@flujo.com",
                 "adminNombre":"Admin de flujo","adminCorreo":"%s","adminPassword":"%s"}
                """.formatted(s, s, s, correo, CLAVE)));
        String token = texto(esperar(200, post("/api/auth/login", null, login(correo, CLAVE))), "token");
        return new Empresa(id(r), token);
    }

    private static String login(String correo, String clave) {
        return "{\"correo\":\"%s\",\"password\":\"%s\"}".formatted(correo, clave);
    }

    private static String actividad(String nombre, long laneId, int x) {
        return "{\"nombre\":\"%s\",\"tipo\":\"TAREA_USUARIO\",\"laneId\":%d,\"posicionX\":%d,\"posicionY\":null}"
                .formatted(nombre, laneId, x);
    }

    private static String arco(String tipoOrigen, long origen, String tipoDestino, long destino, String condicion) {
        return "{\"tipoOrigen\":\"%s\",\"origenId\":%d,\"tipoDestino\":\"%s\",\"destinoId\":%d,\"etiqueta\":null,\"condicion\":%s}"
                .formatted(tipoOrigen, origen, tipoDestino, destino, condicion);
    }

    private static String esperar(int estado, HttpResponse<String> r) {
        assertEquals(estado, r.statusCode(), r.request().method() + " " + r.uri() + " -> " + r.body());
        return r.body();
    }

    private static long id(String json) {
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(json);
        assertTrue(m.find(), "Sin id en " + json);
        return Long.parseLong(m.group(1));
    }

    private static String texto(String json, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        assertTrue(m.find(), "No se encontró " + campo + " en " + json);
        return m.group(1);
    }

    private HttpResponse<String> get(String ruta, String token) throws IOException, InterruptedException {
        return enviar(peticion(ruta, token).GET());
    }

    private HttpResponse<String> delete(String ruta, String token) throws IOException, InterruptedException {
        return enviar(peticion(ruta, token).DELETE());
    }

    private HttpResponse<String> patch(String ruta, String token) throws IOException, InterruptedException {
        return enviar(peticion(ruta, token).method("PATCH", HttpRequest.BodyPublishers.noBody()));
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
}
