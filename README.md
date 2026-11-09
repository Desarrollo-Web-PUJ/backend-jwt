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
cp .env.example .env          # cambie DB_PASSWORD
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
- `frontend`: compila Angular y lo sirve con nginx. nginx reenvía `/api` al backend, así el navegador ve un único origen: no hace falta CORS y la cookie de sesión funciona sin configuración extra.

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

## Arquitectura

```
controller  →  service (interfaz + impl)  →  repository  →  entity
     ↑               ↑
    dto           mapper
```

| Paquete | Responsabilidad |
| --- | --- |
| `controller` | Solo HTTP: recibe el DTO, obtiene la sesión, delega al servicio y elige el código de estado. Documentado con `@Tag` y `@Operation`. |
| `service` | Reglas de negocio de cada historia de usuario. Interfaz + implementación. `@Transactional(readOnly = true)` a nivel de clase y `@Transactional` en las escrituras. |
| `mapper` | Conversión entidad → DTO en componentes Spring. Las entidades nunca salen del backend. |
| `dto` | Contratos de entrada (con Bean Validation) y de salida de la API. |
| `repository` | Spring Data JPA. Las consultas agregadas devuelven DTO mediante proyecciones (`SELECT new ...DTO(...)`), no `Object[]` ni mapas. |
| `sesion` | `SesionActual` guarda en la sesión HTTP solo el id del usuario. En cada petición recarga rol y empresa desde la base, así un cambio de rol o una desactivación aplica de inmediato. |
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

## Autenticación con JWT

La API acepta un token JWT (firmado con HMAC-SHA256) en el encabezado `Authorization`.

1. `POST /api/auth/token` con `{"correo": "...", "password": "..."}` devuelve `{token, tipo: "Bearer", expiraEnSegundos, usuario}`.
2. En cada petición se envía `Authorization: Bearer <token>`.
3. Si el token falta, fue alterado o venció, la API responde 401.

| Archivo | Responsabilidad |
| --- | --- |
| `seguridad/JwtService` | Genera y valida el token (firma y vencimiento). |
| `sesion/SesionActual` | Lee el token de la petición; si no hay token, usa la sesión HTTP. |
| `controller/AuthController` | Endpoint `POST /api/auth/token`. |

El token solo guarda el id del usuario (`sub`), la emisión (`iat`) y el vencimiento (`exp`); el rol y la empresa se consultan en la base de datos en cada petición.
Configuración: `JWT_SECRET` (mínimo 32 caracteres) y `JWT_EXPIRACION_MINUTOS` (60 por defecto).
En Swagger: ejecute `/api/auth/token`, pulse **Authorize** y pegue el token.

## Seguridad y sesión

- Contraseñas con BCrypt.
- Login con un mensaje genérico ante credenciales incorrectas.
- Cookie `JSESSIONID` `HttpOnly` y `SameSite=Lax`. Al iniciar sesión se invalida la sesión anterior para evitar *session fixation*.

| Endpoint | Función |
| --- | --- |
| `POST /api/auth/login` | Inicia sesión. |
| `GET /api/auth/sesion` | Devuelve el usuario actual. |
| `POST /api/auth/logout` | Cierra la sesión (204). |

| Rol | Permisos |
| --- | --- |
| `ADMINISTRADOR` | Todo, incluidos usuarios, roles de proceso y eliminación de procesos. |
| `EDITOR` | Modela procesos. |
| `LECTURA` | Solo consulta. |

Los procesos compartidos por otra empresa son de solo lectura (HU-23).

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
  | 401 | Sin sesión |
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

`contextLoads` necesita una base PostgreSQL disponible, por ejemplo `docker compose up db`.
