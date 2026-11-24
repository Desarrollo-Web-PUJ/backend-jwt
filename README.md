# Backend · Editor y visualizador de procesos

API REST multiempresa para modelar procesos de negocio: pools, lanes, actividades, gateways, arcos, eventos y flujos de mensaje.
Está construida con Spring Boot 4, Java 17, JPA y PostgreSQL.

## Ejecutar con Docker (recomendado)

Clone los dos repositorios uno junto al otro:

```
carpeta-del-proyecto/
├── backend/    ← este repositorio (contiene docker-compose.yml)
└── frontend/
```

```bash
cd backend
cp .env.example .env          # cambie DB_PASSWORD y JWT_SECRET
docker compose up --build
```

| Servicio | URL |
| --- | --- |
| Aplicación (Angular + nginx) | http://localhost:4200 |
| API | http://localhost:8080/api |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI (JSON) | http://localhost:8080/v3/api-docs |

Compose levanta tres contenedores:

- `db`: PostgreSQL 17, con *healthcheck* y volumen `datos-postgres`.
- `backend`: imagen multi-etapa. Maven compila y la imagen final usa JRE 17 Alpine con un usuario sin privilegios. Arranca solo cuando la base está sana.
- `frontend`: compila Angular y lo sirve con nginx. nginx reenvía `/api` al backend, así el navegador ve un único origen y no hace falta CORS.

Comandos útiles:

- `docker compose down` detiene los contenedores.
- `docker compose down -v` además borra los datos.

## Ejecutar sin Docker

Requisitos: JDK 17 y PostgreSQL.

```bash
./mvnw spring-boot:run
```

La configuración se lee de variables de entorno, con valores por defecto para desarrollo local (ver `application.properties`):

| Variable | Por defecto |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/proyecto_db` |
| `DB_USERNAME` / `DB_PASSWORD` | `proyecto_user` / la clave local del equipo |
| `JPA_DDL_AUTO` | `update` |
| `JPA_SHOW_SQL` | `false` |

## CI/CD con GitHub Actions

Cada repositorio tiene su pipeline en `.github/workflows/ci-cd.yml`, que corre en cada push y pull request a `main`:

| Etapa | Dónde corre | Qué hace |
| --- | --- | --- |
| `pruebas` | GitHub (Ubuntu) | `./mvnw -B verify`: compila y corre las pruebas unitarias y de integración (con H2). Guarda el reporte. |
| `imagen` | GitHub (Ubuntu) | Construye la imagen Docker del backend. |
| `despliegue` | Runner propio (`self-hosted`) | Descarga backend y frontend, crea el `.env` con los secretos y ejecuta `docker compose up -d --build`. Verifica que la API y el frontend respondan. |

El despliegue se despliega en **tres componentes por separado** (base de datos, backend y frontend), cada uno en su propio contenedor.

Para activar el despliegue automático:
1. Registrar un runner propio en el equipo que hará de servidor: *Settings → Actions → Runners → New self-hosted runner* (requiere Docker).
2. Crear los secretos `DB_PASSWORD` y `JWT_SECRET`: *Settings → Secrets and variables → Actions → Secrets*.
3. Crear la variable `DESPLIEGUE_HABILITADO` con valor `true`: *Settings → Secrets and variables → Actions → Variables*.

Sin el paso 3, las etapas de pruebas e imagen corren igual y el despliegue se omite.

## Arquitectura

```
controller  →  service (interfaz + impl)  →  repository  →  entity
     ↑               ↑
    dto           mapper
```

| Paquete | Responsabilidad |
| --- | --- |
| `controller` | Solo HTTP: recibe el DTO, obtiene el usuario autenticado, delega al servicio y elige el código de estado. Documentado con `@Tag` y `@Operation`. |
| `service` | Reglas de negocio de cada historia de usuario. Interfaz + implementación. `@Transactional(readOnly = true)` a nivel de clase y `@Transactional` en las escrituras. |
| `mapper` | Conversión entidad → DTO en componentes Spring. Las entidades nunca salen del backend. |
| `dto` | Contratos de entrada (con Bean Validation) y de salida de la API. |
| `repository` | Spring Data JPA. Las consultas agregadas devuelven DTO mediante proyecciones (`SELECT new ...DTO(...)`), no `Object[]` ni mapas. |
| `seguridad` | Spring Security y JWT: filtro, servicio de tokens y respuestas 401/403 (ver *Seguridad*). |
| `sesion` | `SesionActual` entrega el usuario autenticado del `SecurityContext` (rol y empresa leídos de la base en cada petición). |
| `exception` | Excepciones de dominio y `GlobalExceptionHandler`. |
| `config` | Configuración de OpenAPI. |

Servicios transversales que evitan código repetido:

- `ValidadorAcceso`: permisos por rol, y comprobar que el proceso existe, pertenece a la empresa y está activo.
- `HistorialService`: registra cada cambio con el usuario que lo hizo y la fecha (HU-05).
- `DiagramaProcesoService`: arma el detalle completo del proceso para la vista del diagrama (HU-07).
- `PoolService.crearPoolPropio`: crea el pool de la empresa al crear un proceso.

### Ajustes tras la revisión de la primera entrega

- Las consultas que devolvían `Map`/`Object[]` y se recorrían con `.map(...)` y casts ahora devuelven DTO tipados.
  - Ejemplo: `LaneRepository.findUsoDeRoles` devuelve `List<UsoRolProcesoDTO>`.
  - Las conversiones viven en clases `mapper`.
- Los servicios ya no dependen de muchos repositorios ajenos.
  - Cada servicio usa su repositorio y delega en otros servicios (pool, historial, diagrama, validador).
  - Ejemplo: `ProcesoServiceImpl` pasó de 12 repositorios a 3.
- Los controladores ya no tienen lógica: no leen atributos sueltos de la sesión ni arman respuestas a mano.

## Seguridad: Spring Security + JWT

La API es **sin estado** (`SessionCreationPolicy.STATELESS`): no hay sesión HTTP y cada petición trae su token JWT. El token se genera y se valida con la librería [JJWT](https://github.com/jwtk/jjwt) 0.12.6 (firma HMAC-SHA256).

```
POST /api/auth/login ──► AuthService valida correo y contraseña (BCrypt) ──► JwtService.generar ──► {token}

Petición con "Authorization: Bearer <token>"
  ──► JwtAuthenticationFilter: JwtService.validar (firma y vencimiento)
        ──► carga el usuario de la base (rol y empresa vigentes)
        ──► SecurityContext con ROLE_ADMINISTRADOR | ROLE_EDITOR | ROLE_LECTURA
  ──► SecurityConfig: reglas por rol (403 si no alcanza)
  ──► Servicio: ValidadorAcceso filtra por empresa (aislamiento entre organizaciones)
```

| Archivo | Responsabilidad |
| --- | --- |
| `config/SecurityConfig` | `SecurityFilterChain`: rutas públicas, reglas por rol, API sin estado, respuestas 401/403. |
| `seguridad/JwtAuthenticationFilter` | Filtro de Spring Security que valida el token y autentica la petición. |
| `seguridad/JwtService` | Genera y valida el token con JJWT (`Jwts.builder()` y `Jwts.parser()`). |
| `seguridad/RespuestaErrorSeguridad` | Escribe los 401/403 con el formato `{mensaje, errores}`. |
| `sesion/SesionActual` | Entrega a los controladores el usuario autenticado del `SecurityContext`. |
| `service/ValidadorAcceso` | Reglas de negocio: proceso de la empresa, activo, permisos de edición. |

| Endpoint | Acceso | Función |
| --- | --- | --- |
| `POST /api/auth/login` | Público | Devuelve `{token, tipo: "Bearer", expiraEnSegundos, usuario}`. |
| `POST /api/empresas/registro` | Público | Registra la empresa y su administrador. |
| `GET /api/auth/sesion` | Autenticado | Usuario dueño del token. |
| `POST /api/auth/logout` | Autenticado | 204; el cliente descarta su token. |

| Rol | Permisos (además de consultar) |
| --- | --- |
| `ADMINISTRADOR` | Gestiona usuarios y roles de proceso, elimina y comparte procesos, y todo lo del editor. |
| `EDITOR` | Crea y edita procesos y modela su diagrama. |
| `LECTURA` | Solo consulta. |

Los procesos compartidos por otra empresa son de solo lectura (HU-23). Un proceso de otra empresa no compartido responde 404.

- El token solo guarda `sub` (id del usuario), `iat` y `exp`; el rol y la empresa se leen de la base en cada petición, así un cambio de rol o una desactivación aplica de inmediato.
- Contraseñas con BCrypt y mensaje genérico ante credenciales incorrectas.
- Configuración: `JWT_SECRET` (mínimo 32 caracteres) y `JWT_EXPIRACION_MINUTOS` (60 por defecto).
- En Swagger: ejecute `POST /api/auth/login`, pulse **Authorize** y pegue el token.

## Convenciones de la API

- **Paginación.** `GET /api/procesos` y `GET /api/roles-proceso` aceptan `page`, `size`, `sort` y filtros. Responden con:
  ```json
  { "content": [], "number": 0, "size": 10, "totalElements": 0, "totalPages": 0, "first": true, "last": true }
  ```
- **Errores.** Todos los errores comparten un formato:
  ```json
  { "mensaje": "Datos inválidos", "errores": [{ "campo": "nombre", "mensaje": "El nombre es obligatorio" }] }
  ```

  | Código | Cuándo |
  | --- | --- |
  | 400 | Validación u operación inválida |
  | 401 | Sin token, token inválido o vencido |
  | 403 | Sin permiso |
  | 404 | No existe |
  | 409 | Duplicado |

- **Eliminaciones.** Requieren `?confirmar=true`, y son lógicas (`activo = false`).
  - Un gateway sin confirmar responde qué elementos se perderían (HU-16).

## Documentación de la API

- **Swagger UI:** http://localhost:8080/swagger-ui.html (springdoc-openapi).
- **Postman:** `postman/ProcesosAPI.postman_collection.json`.
  1. En Postman: *Import* → seleccionar el archivo.
  2. Revise la variable `baseUrl` (por defecto `http://localhost:8080`).
  3. Ejecute la colección completa con *Run collection*, en orden.

  Las 36 peticiones:

  - registran una empresa nueva (con un sufijo único, así se puede repetir);
  - inician sesión y modelan el proceso de solicitud de vacaciones del enunciado;
  - prueban los casos de error (duplicados, validaciones, permisos, confirmación);
  - verifican cada respuesta con *tests* automáticos.

## Pruebas

```bash
./mvnw test
```

No necesitan PostgreSQL: usan una base H2 en memoria (`src/test/resources/application.properties`).

`SeguridadApiIntegrationTest` levanta la aplicación completa en un puerto aleatorio y la consume por HTTP (17 casos):

- **Autenticación:** registro público, login correcto e incorrecto, sin token, token falso y token alterado (401).
- **Casos de uso:** crear, listar y consultar procesos; historial con su autor.
- **Autorización por rol:** EDITOR crea procesos; LECTURA no crea (403) pero sí consulta; EDITOR no gestiona usuarios ni elimina procesos (403).
- **Aislamiento entre empresas:** una empresa no ve los procesos de otra (404), no gestiona sus usuarios (403), y un proceso compartido se ve pero no se edita.
