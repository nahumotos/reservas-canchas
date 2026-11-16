# reservas-service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Microservicio `reservas-service` que reserva turnos de 1 hora en canchas de `canchas-service`, impide la doble reserva desde la base y expira las reservas impagas.

**Architecture:** Paquetes por funcionalidad (`reserva`, `disponibilidad`, `turno`, `canchas`, `config`, `error`), igual que canchas-service. Lee canchas y horarios por HTTP con `RestClient` (base de datos propia, sin FK a la otra base). La unicidad del turno la garantiza un índice único parcial de PostgreSQL; la aplicación sólo traduce su violación a 409.

**Tech Stack:** Spring Boot 4.1.2, Java 25, Web MVC, Data JPA/Hibernate 7, PostgreSQL 18, Flyway, Bean Validation, Actuator, springdoc 3.1.0, JUnit 5, Testcontainers 2.x, Mockito (`@MockitoBean`), `MockRestServiceServer`.

**Spec:** pedido del usuario del 2026-11-16 (pasos 1 a 8); sus valores exactos están en *Restricciones globales*.

## Restricciones globales

- Proyecto generado con start.spring.io: Maven, Boot `4.1.2`, Java `25`, grupo `com.nahumotos`, artifact `reservas-service`, paquete `com.nahumotos.reservas_service`, deps `web,data-jpa,postgresql,flyway,validation,actuator,testcontainers`; sumar `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0`.
- Puerto `8082`; base `jdbc:postgresql://localhost:5432/reservas`, usuario `${DB_USER:app}` / `${DB_PASSWORD:cambiame}`; `ddl-auto: validate`, `open-in-view: false`, actuator `health,info`, springdoc `override-with-generic-response: false` y `default-produces-media-type: application/json` (igual que canchas-service).
- Primera línea del `main`: `TimeZone.setDefault(TimeZone.getTimeZone("UTC"));` con el comentario de postgres:18 (ver canchas-service).
- Propiedades: `canchas-service.url=http://localhost:8081`, `reservas.zona-horaria=America/Argentina/Buenos_Aires`, `reservas.vencimiento-pago=10m`, `reservas.expiracion.cron="0 * * * * *"`.
- Fechas y horas de reserva: `LocalDate`/`LocalTime` en hora local del club; instantes (`vence_en`, `creada_en`): `Instant` ↔ `TIMESTAMPTZ`. "Ahora" sale siempre de un bean `Clock` en la zona configurada; nunca `LocalDateTime.now()` sin reloj.
- Estilo de canchas-service: tabs, nombres de dominio en español, DTOs como records con Bean Validation, controllers nunca devuelven entidades, `ProblemDetail` para todo error, `@Tag`/`@Operation`/`@ApiResponse` en cada endpoint.
- Testcontainers con imagen `postgres:18`. Los tests de integración se llaman `*IntegrationTest` para que corran con `.\mvnw.cmd test`.
- **Nada de RabbitMQ ni pagos reales. No hacer commits** (por eso no hay pasos de commit).
- Todos los comandos se corren desde `reservas-service/`.

## Revisar especialmente

1. **Club que cierra a la medianoche o pasada** (canchas-service lo permite, ej. 08:00–00:00 o 20:00–02:00): el turno 23:00–00:00 (con `hora_fin` < `hora_inicio`) se puede reservar y aparece en la disponibilidad. Tests en Tasks 2, 5 y 7 (pedido explícito del usuario).
2. **Email con mayúsculas o espacios** (`" Ana@Mail.com "`): se guarda normalizado y `GET ?clienteEmail=ana@mail.com` la encuentra. Test en Task 5.
3. **Confirmar una reserva vencida que el job todavía no marcó**: 409, no 200. Test en Task 8.
4. **Turno cancelado o expirado vuelve a estar libre** (el índice es parcial): se puede reservar de nuevo. Tests en Tasks 4, 8 y 9.
5. **canchas-service lento o caído**: 503 tras el timeout de lectura (3 s), sin colgar el hilo. Test en Task 3.

## Estructura de archivos

```
reservas-service/
  pom.xml                                   (generado + springdoc)
  src/main/resources/application.yml        (reemplaza application.properties)
  src/main/resources/db/migration/V1__crear_reserva.sql
  src/main/java/com/nahumotos/reservas_service/
    ReservasServiceApplication.java         UTC, @EnableScheduling, @ConfigurationPropertiesScan, @OpenAPIDefinition
    config/ReservasProperties.java          record (zonaHoraria, vencimientoPago)
    config/RelojConfig.java                 @Bean Clock en la zona configurada
    turno/Turnos.java                       reglas puras de turnos (sin Spring)
    canchas/CanchasClient.java              RestClient a canchas-service + traducción de errores
    canchas/CanchaRemota.java, ClubRemoto.java          lo que leemos de canchas-service
    canchas/CanchasServiceProperties.java   record (url, timeoutConexion, timeoutLectura)
    canchas/CanchasClientConfig.java        @Bean CanchasClient con timeouts
    reserva/Reserva.java, EstadoReserva.java, ReservaRepository.java
    reserva/ReservaService.java, ReservaController.java
    reserva/CrearReservaRequest.java, ReservaResponse.java
    reserva/ExpiracionDeReservas.java       @Scheduled + UPDATE masivo
    disponibilidad/DisponibilidadService.java, DisponibilidadController.java, DisponibilidadResponse.java
    error/GlobalExceptionHandler.java
    error/RecursoNoEncontradoException.java (404), ConflictoException.java (409),
    error/SolicitudInvalidaException.java (400), ServicioNoDisponibleException.java (503)
  src/test/java/com/nahumotos/reservas_service/
    TestcontainersConfiguration.java        (generado; imagen postgres:18)
    TestReservasServiceApplication.java     (generado, sin cambios)
    ReservasServiceApplicationTests.java    (generado; pasa a extender PruebaDeIntegracion)
    PruebaDeIntegracion.java                base: contexto, Postgres, mocks, reloj
    RelojDeTest.java                        Clock ajustable
    turno/TurnosTest.java
    canchas/CanchasClientTest.java
    reserva/ReservaTest.java
    reserva/ReservaRepositoryIntegrationTest.java
    reserva/ReservaApiIntegrationTest.java
    reserva/ReservaConcurrenciaIntegrationTest.java
    reserva/ExpiracionDeReservasIntegrationTest.java
    disponibilidad/DisponibilidadIntegrationTest.java
```

---

### Task 1: Proyecto base que levanta contra Postgres en Testcontainers

**Files:**
- Create: `reservas-service/` desde start.spring.io (parámetros en Restricciones globales)
- Modify: `pom.xml` (+ springdoc 3.1.0), `ReservasServiceApplication.java` (línea UTC + comentario)
- Create: `src/main/resources/application.yml`; Delete: `application.properties`
- Modify: `src/test/.../TestcontainersConfiguration.java` (`postgres:latest` → `postgres:18`)

Código generado: no hay fase roja; el test generado `contextLoads` sirve para comprobar que Docker y Testcontainers funcionan en esta máquina.

- [ ] **Step 1:** Descargar el zip de start.spring.io, descomprimirlo en `reservas-service/`, sumar springdoc y escribir `application.yml` con todas las propiedades de Restricciones globales (incluye `canchas-service.*` con `timeout-conexion: 2s`, `timeout-lectura: 3s`, y `reservas.*`).
- [ ] **Step 2:** `.\mvnw.cmd test -Dtest=ReservasServiceApplicationTests` → PASS (`Tests run: 1, Failures: 0`; sin migraciones Flyway sólo avisa "No migrations found"). Si falla por Docker/Testcontainers, resolverlo acá: todo lo demás depende de esto.

### Task 2: Reglas de turnos (`turno/Turnos`)

**Files:** Create `turno/Turnos.java`; Test `turno/TurnosTest.java`

**Interfaces — Produces:**
- `Turnos.DURACION: Duration` = 1 hora
- `static List<LocalTime> delDia(LocalTime apertura, LocalTime cierre)` — horas de inicio, ordenadas
- `static boolean empiezaEnPunto(LocalTime hora)`
- `static boolean empezo(LocalDate fecha, LocalTime horaInicio, Clock reloj)` — `true` si el inicio no es posterior a `LocalDateTime.now(reloj)`

- [ ] **Step 1: Tests que fallan**

```java
delDia_horarioNormal_turnosEnPuntoQueTerminanAntesDelCierre   // (08:00, 23:00) -> 08:00..22:00, size 15
delDia_aperturaNoEnPunto_empiezaEnLaHoraSiguiente             // (08:30, 12:00) -> [09:00, 10:00, 11:00]
delDia_cierreDespuesDeMedianoche_incluyeMadrugadaYNoche       // (20:00, 02:00) -> [00:00, 01:00, 20:00, 21:00, 22:00, 23:00]
delDia_cierreALaMedianoche_incluyeElTurnoDe23                 // (08:00, 00:00) -> 08:00..23:00, size 16
empiezaEnPunto_soloMinutosSegundosYNanosEnCero                // 18:00 true; 18:30 false; 18:00:01 false
empezo_usaLaZonaDelRelojNoUtc
    // reloj fijo 2030-06-10T02:00:00Z en America/Argentina/Buenos_Aires (= 09/06 23:00 local)
    // empezo(2030-06-10, 00:00) == false ; empezo(2030-06-09, 23:00) == true
empezo_turnoEnCursoCuentaComoEmpezado                         // reloj 12:10 local: empezo(hoy, 12:00) == true; (hoy, 13:00) == false
```

- [ ] **Step 2:** `.\mvnw.cmd test -Dtest=TurnosTest` → FAIL (no compila: `Turnos` no existe).
- [ ] **Step 3: Implementar.** Algoritmo de `delDia` (trabaja en minutos del día para evitar el salto de medianoche):

```java
int abre = apertura.toSecondOfDay() / 60, cierra = cierre.toSecondOfDay() / 60;
List<int[]> abierto = cierra > abre ? List.of(new int[]{abre, cierra})
                                    : List.of(new int[]{0, cierra}, new int[]{abre, 24 * 60}); // cierra pasada la medianoche (igual = 24 h)
// turno h válido si [h*60, h*60+60) cabe entero en algún intervalo de "abierto"
```

- [ ] **Step 4:** `.\mvnw.cmd test -Dtest=TurnosTest` → PASS (6 tests).

### Task 3: Cliente de canchas-service (`canchas/`) y errores 404/503

**Files:** Create `canchas/CanchasClient.java`, `CanchaRemota.java`, `ClubRemoto.java`, `CanchasServiceProperties.java`, `CanchasClientConfig.java`, `error/RecursoNoEncontradoException.java`, `error/ServicioNoDisponibleException.java`; Modify `ReservasServiceApplication.java` (`@ConfigurationPropertiesScan`); Test `canchas/CanchasClientTest.java`

**Interfaces — Produces:**
- `record CanchaRemota(Long id, Long clubId, String nombre, String deporte, BigDecimal precioHora, boolean activa)` y `record ClubRemoto(Long id, String nombre, LocalTime horaApertura, LocalTime horaCierre)`, ambos con `@JsonIgnoreProperties(ignoreUnknown = true)` (lector tolerante: no se rompe si canchas-service agrega campos).
- `class CanchasClient(RestClient restClient)` (no es `@Component`): `CanchaRemota obtenerCancha(Long canchaId)`, `ClubRemoto obtenerClub(Long clubId)`.
- `RecursoNoEncontradoException(String mensaje)`; `ServicioNoDisponibleException(String mensaje, Throwable causa)`.
- `@ConfigurationProperties("canchas-service") record CanchasServiceProperties(URI url, Duration timeoutConexion, Duration timeoutLectura)`.
- `CanchasClientConfig`: `@Bean CanchasClient` con `RestClient.builder().baseUrl(url)` + `JdkClientHttpRequestFactory` (connect timeout en el `HttpClient`, `setReadTimeout(timeoutLectura)`).

- [ ] **Step 1: Tests que fallan** (unitarios, `MockRestServiceServer.bindTo(RestClient.builder())`, sin Spring):

```java
obtenerCancha_200_mapeaCamposEIgnoraDesconocidos   // body con "campoNuevo":"x" -> new CanchaRemota(10L, 1L, "Cancha 1", "FUTBOL5", new BigDecimal("25000.00"), true)
obtenerClub_200_mapeaHorario                        // "08:00:00"/"23:00:00" -> ClubRemoto(1L, "Club", 08:00, 23:00)
obtenerCancha_404_lanzaRecursoNoEncontrado          // mensaje "No existe la cancha con id 99"
obtenerClub_404_lanzaRecursoNoEncontrado            // mensaje "No existe el club con id 7"
obtenerCancha_500_lanzaServicioNoDisponible
obtenerCancha_sinConexion_lanzaServicioNoDisponible // withException(new ConnectException())
obtenerCancha_timeout_lanzaServicioNoDisponible     // withException(new HttpTimeoutException("timeout"))
```

- [ ] **Step 2:** `.\mvnw.cmd test -Dtest=CanchasClientTest` → FAIL (no compila).
- [ ] **Step 3: Implementar.** `retrieve().onStatus(...)`: 404 → `RecursoNoEncontradoException`, 5xx → `ServicioNoDisponibleException`; `catch (ResourceAccessException)` (I/O, conexión rechazada, timeout) → `ServicioNoDisponibleException("canchas-service no está disponible", e)`. Otros 4xx se propagan (serían un bug nuestro → 500).
- [ ] **Step 4:** `.\mvnw.cmd test -Dtest=CanchasClientTest` → PASS (7 tests).

### Task 4: Tabla `reserva`, entidad y el índice único parcial

**Files:** Create `db/migration/V1__crear_reserva.sql`, `reserva/Reserva.java`, `reserva/EstadoReserva.java`, `reserva/ReservaRepository.java`, test `PruebaDeIntegracion.java`, `RelojDeTest.java`, `reserva/ReservaRepositoryIntegrationTest.java`; Modify `ReservasServiceApplicationTests.java` (extiende `PruebaDeIntegracion` y pierde su `@SpringBootTest`/`@Import`, para reutilizar el mismo contexto)

**Interfaces — Produces:**
- `enum EstadoReserva { PENDIENTE_PAGO, CONFIRMADA, CANCELADA, EXPIRADA; static final Set<EstadoReserva> OCUPAN_TURNO = EnumSet.of(PENDIENTE_PAGO, CONFIRMADA); }` (debe coincidir con el `WHERE` del índice).
- `Reserva(Long canchaId, Long clubId, LocalDate fecha, LocalTime horaInicio, String clienteNombre, String clienteEmail, Instant creadaEn, Instant venceEn)`: estado `PENDIENTE_PAGO`, `horaFin = horaInicio.plus(Turnos.DURACION)`; constructor protegido para JPA; getters; `@Version Long version`.
- `ReservaRepository extends JpaRepository<Reserva, Long>`.
- `PruebaDeIntegracion` (abstracta, pública): `@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "reservas.expiracion.cron=-")`, `@AutoConfigureMockMvc`, `@Import({TestcontainersConfiguration.class, PruebaDeIntegracion.RelojDeTestConfig.class})` (clase anidada `@TestConfiguration` con `@Bean @Primary RelojDeTest`); campos `protected` `MockMvc mockMvc`, `ReservaRepository reservaRepository`, `JdbcTemplate jdbc`, `RelojDeTest reloj`, `@MockitoBean CanchasClient canchasClient`. Constantes: `CANCHA_ID = 10L`, `CLUB_ID = 1L`, `HOY = 2030-06-10`, `MANIANA = 2030-06-11`, `AHORA = 2030-06-10T12:00` hora argentina. `@BeforeEach`: `reservaRepository.deleteAll()`, `reloj.fijar(AHORA)`, stubs `obtenerCancha(10)` → activa del club 1 y `obtenerClub(1)` → 08:00–23:00. Una sola base para que todos los tests compartan contexto y contenedor.
- `RelojDeTest extends Clock`: `fijar(LocalDateTime local)`, `avanzar(Duration)`, zona `America/Argentina/Buenos_Aires`; registrado como `@Primary`.

- [ ] **Step 1: Tests que fallan**

```java
indiceParcial_rechazaSegundaReservaActivaDelMismoTurno
    // saveAndFlush(reserva 10/MANIANA/18:00) ; segunda igual -> assertThrows(DataIntegrityViolationException.class)
indiceParcial_permiteReservarDeNuevoUnTurnoCancelado
    // primera; jdbc.update("update reserva set estado = 'CANCELADA'"); segunda igual -> se guarda (count == 2)
constructor_calculaHoraFinYArrancaPendientePago            // 18:00 -> horaFin 19:00, estado PENDIENTE_PAGO, version 0 tras guardar
```

- [ ] **Step 2:** `.\mvnw.cmd test -Dtest=ReservaRepositoryIntegrationTest` → FAIL (no compila).
- [ ] **Step 3: Implementar.** Migración (el índice es la pieza central):

```sql
CREATE TABLE reserva (
    id BIGSERIAL PRIMARY KEY, cancha_id BIGINT NOT NULL, club_id BIGINT NOT NULL,
    fecha DATE NOT NULL, hora_inicio TIME NOT NULL, hora_fin TIME NOT NULL,
    cliente_nombre VARCHAR(100) NOT NULL, cliente_email VARCHAR(200) NOT NULL,
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('PENDIENTE_PAGO','CONFIRMADA','CANCELADA','EXPIRADA')),
    vence_en TIMESTAMPTZ NOT NULL, creada_en TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_reserva_turno_activo ON reserva (cancha_id, fecha, hora_inicio)
    WHERE estado IN ('PENDIENTE_PAGO', 'CONFIRMADA');
CREATE INDEX ix_reserva_cliente_email ON reserva (cliente_email);
CREATE INDEX ix_reserva_pendiente_vence_en ON reserva (vence_en) WHERE estado = 'PENDIENTE_PAGO';
```

  Sin FK a `cancha`/`club`: viven en la base de otro servicio.
- [ ] **Step 4:** `.\mvnw.cmd test -Dtest="ReservaRepositoryIntegrationTest,ReservasServiceApplicationTests"` → PASS.

### Task 5: Crear, obtener y listar reservas (API + errores)

**Files:** Create `config/ReservasProperties.java`, `config/RelojConfig.java`, `error/ConflictoException.java`, `error/SolicitudInvalidaException.java`, `error/GlobalExceptionHandler.java`, `reserva/CrearReservaRequest.java`, `reserva/ReservaResponse.java`, `reserva/ReservaService.java`, `reserva/ReservaController.java`; Modify `ReservasServiceApplication.java` (`@OpenAPIDefinition`); Test `reserva/ReservaApiIntegrationTest.java`

**Interfaces:**
- Consumes: `CanchasClient`, `Turnos`, `Reserva`, `ReservaRepository`.
- Produces: `@ConfigurationProperties("reservas") record ReservasProperties(ZoneId zonaHoraria, Duration vencimientoPago)`; `@Bean Clock reloj()` = `Clock.system(zonaHoraria)`; `ConflictoException(String mensaje)`, `SolicitudInvalidaException(String mensaje)` (ambas `RuntimeException`).
- `record CrearReservaRequest(@NotNull @Positive Long canchaId, @NotNull LocalDate fecha, @NotNull LocalTime horaInicio, @NotBlank @Size(max=100) String clienteNombre, @NotBlank @Email @Size(max=200) String clienteEmail)` con `@Schema(example=...)`.
- `record ReservaResponse(Long id, Long canchaId, Long clubId, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, String clienteNombre, String clienteEmail, EstadoReserva estado, Instant venceEn, Instant creadaEn)` + `static desde(Reserva)`.
- `ReservaService`: `ReservaResponse crear(CrearReservaRequest)`, `ReservaResponse obtener(Long id)`, `List<ReservaResponse> listarPorCliente(String email)`.
- `GlobalExceptionHandler`: copia del de canchas-service + `ConflictoException`→409 "Conflicto", `SolicitudInvalidaException`→400 "Solicitud inválida", `ServicioNoDisponibleException`→503 "Servicio no disponible", `ObjectOptimisticLockingFailureException`→409.

- [ ] **Step 1: Tests que fallan** (body base: `{canchaId:10, fecha:"2030-06-11", horaInicio:"18:00", clienteNombre:"Ana", clienteEmail:"ana@mail.com"}`)

```java
crear_turnoValido_201ConLocationYPendientePago
    // 201; header Location termina en "/api/reservas/{id}"; estado PENDIENTE_PAGO; clubId 1;
    // horaFin "19:00:00"; creadaEn "2030-06-10T15:00:00Z"; venceEn "2030-06-10T15:10:00Z"
crear_turnoFueraDeHorario_400             // 07:00 -> 400, detail contiene "fuera del horario"
crear_turnoQueTerminaDespuesDelCierre_400 // 23:00 con cierre 23:00 -> 400
crear_turnoDe23a00ConClubAbierto_201      // club 08:00–00:00; 23:00 -> 201, horaInicio "23:00:00", horaFin "00:00:00" (hora_fin < hora_inicio)
crear_horaNoEnPunto_400                   // 18:30 -> 400, detail contiene "en punto"
crear_turnoEnElPasado_400                 // HOY 11:00 -> 400 ; HOY 12:00 (ya empezó) -> 400
crear_canchaInactiva_409
crear_canchaInexistente_404               // mock lanza RecursoNoEncontradoException("No existe la cancha con id 10")
crear_canchasServiceCaido_503             // mock lanza ServicioNoDisponibleException -> 503, title "Servicio no disponible"
crear_bodyVacio_400ConErroresPorCampo     // errores[].campo == [canchaId, clienteEmail, clienteNombre, fecha, horaInicio]
obtener_existente_200 / obtener_inexistente_404
listarPorCliente_normalizaEmail           // crear con " Ana@Mail.com " ; GET ?clienteEmail=ANA@mail.com -> 1 reserva, clienteEmail "ana@mail.com"
listarPorCliente_sinParametro_400
```

- [ ] **Step 2:** `.\mvnw.cmd test -Dtest=ReservaApiIntegrationTest` → FAIL (no compila).
- [ ] **Step 3: Implementar `crear`.** Sin `@Transactional`: las llamadas HTTP no deben retener una conexión a la base. Orden: `obtenerCancha` → si `!activa` `ConflictoException("La cancha 10 no está activa")` → `obtenerClub(cancha.clubId())` → `!empiezaEnPunto` → `SolicitudInvalidaException("Los turnos empiezan en punto: 18:30 no es válido")` → `!delDia(...).contains(hora)` → `"El turno de las 07:00 está fuera del horario del club (08:00 a 23:00)"` → `empezo(...)` → `"El turno del 2030-06-10 a las 11:00 ya empezó o está en el pasado"` → `saveAndFlush` (su propia transacción). Email: `trim().toLowerCase(Locale.ROOT)` al guardar y al buscar. `obtener`/`listarPorCliente` con `@Transactional(readOnly = true)`; listado ordenado por fecha y hora descendente. Controller: `POST /api/reservas` (201 + Location), `GET /api/reservas/{id}`, `GET /api/reservas?clienteEmail=`, con `@Tag("Reservas")`, `@Operation`, `@ApiResponse` por código.
- [ ] **Step 4:** `.\mvnw.cmd test -Dtest=ReservaApiIntegrationTest` → PASS.

### Task 6: Doble reserva concurrente → exactamente un 201 y un 409

**Files:** Modify `reserva/ReservaService.java`; Test `reserva/ReservaConcurrenciaIntegrationTest.java`, `reserva/ReservaApiIntegrationTest.java`

- [ ] **Step 1: Tests que fallan**

```java
// ReservaConcurrenciaIntegrationTest (HTTP real al puerto aleatorio con java.net.http.HttpClient, 2 hilos)
dosPedidosSimultaneosAlMismoTurno_unoCreaYElOtroRecibe409
    // stub obtenerCancha con un CyclicBarrier(2) (await 5 s): ambos hilos pasan las validaciones
    // y llegan juntos al INSERT -> sólo el índice puede frenar al segundo.
    // statuses ordenados == [201, 409]; jdbc count(*) where estado='PENDIENTE_PAGO' and turno == 1;
    // el 409 trae title "Conflicto" y detail "El turno del 2030-06-11 a las 18:00 ya está reservado"
// ReservaApiIntegrationTest
crear_turnoYaReservado_409                 // mismo body dos veces seguidas
```

- [ ] **Step 2:** `.\mvnw.cmd test -Dtest="ReservaConcurrenciaIntegrationTest,ReservaApiIntegrationTest"` → FAIL: el segundo da 500 (la `DataIntegrityViolationException` no está traducida).
- [ ] **Step 3:** En `crear`, capturar `DataIntegrityViolationException` alrededor de `saveAndFlush`: si en la cadena de causas hay una `org.hibernate.exception.ConstraintViolationException` con `getConstraintName()` igual (ignorando mayúsculas) a `ux_reserva_turno_activo` → `ConflictoException(...)`; si no, relanzar. Sin chequeo previo de "existe": la base es la única fuente de verdad.
- [ ] **Step 4:** mismo comando → PASS. Correr el test de concurrencia 3 veces seguidas para descartar que pase por casualidad.

### Task 7: Disponibilidad del día

**Files:** Create `disponibilidad/DisponibilidadService.java`, `DisponibilidadController.java`, `DisponibilidadResponse.java`; Modify `reserva/ReservaRepository.java`; Test `disponibilidad/DisponibilidadIntegrationTest.java`

**Interfaces:**
- `ReservaRepository`: `@Query("select r.horaInicio from Reserva r where r.canchaId = :canchaId and r.fecha = :fecha and r.estado in :estados") List<LocalTime> findHorasOcupadas(Long canchaId, LocalDate fecha, Collection<EstadoReserva> estados)`.
- `record DisponibilidadResponse(Long canchaId, LocalDate fecha, List<TurnoLibre> turnos)` con `record TurnoLibre(LocalTime horaInicio, LocalTime horaFin)` anidado.
- `DisponibilidadService.consultar(Long canchaId, LocalDate fecha)`; endpoint `GET /api/canchas/{canchaId}/disponibilidad?fecha=` con `@DateTimeFormat(iso = DATE)` (sin eso Spring usa el formato corto del locale), `@Tag("Disponibilidad")`.

- [ ] **Step 1: Tests que fallan**

```java
disponibilidad_diaFuturo_excluyeSoloTurnosOcupados
    // reservas MANIANA 18:00 (PENDIENTE_PAGO), 20:00 (CONFIRMADA vía jdbc), 21:00 (CANCELADA vía jdbc)
    // -> 13 turnos, sin 18:00 ni 20:00, con 21:00; primero {"horaInicio":"08:00:00","horaFin":"09:00:00"}
disponibilidad_hoy_excluyeTurnosQueYaEmpezaron   // AHORA 12:00 -> primero 13:00:00, 10 turnos
disponibilidad_clubQueCierraALaMedianoche_incluyeTurnoDe23a00
    // club 08:00–00:00 -> 16 turnos; último {"horaInicio":"23:00:00","horaFin":"00:00:00"}
disponibilidad_fechaPasada_listaVacia
disponibilidad_canchaInactiva_listaVacia
disponibilidad_fechaMalFormada_400               // fecha=11-06-2030
disponibilidad_canchaInexistente_404
```

- [ ] **Step 2:** `.\mvnw.cmd test -Dtest=DisponibilidadIntegrationTest` → FAIL (404, el endpoint no existe).
- [ ] **Step 3:** `Turnos.delDia` − `findHorasOcupadas(..., EstadoReserva.OCUPAN_TURNO)` − `Turnos.empezo`; cancha inactiva → lista vacía. Usa la misma regla que `crear`, así lo que se muestra libre es lo que se puede reservar.
- [ ] **Step 4:** mismo comando → PASS.

### Task 8: Confirmar y cancelar

**Files:** Modify `reserva/Reserva.java`, `ReservaService.java`, `ReservaController.java`; Test `reserva/ReservaTest.java` (unitario), `reserva/ReservaApiIntegrationTest.java`

**Interfaces — Produces:** en `Reserva`: `void confirmar(Instant ahora)`, `void cancelar()`; en `ReservaService` (`@Transactional`): `ReservaResponse confirmar(Long id)`, `ReservaResponse cancelar(Long id)`; endpoints `POST /api/reservas/{id}/confirmar` y `/cancelar`, ambos 200 con `ReservaResponse`.

Máquina de estados (vencida = `!ahora.isBefore(venceEn)`, igual que el `<=` del job):

| desde \ acción | confirmar | cancelar |
|---|---|---|
| PENDIENTE_PAGO no vencida | CONFIRMADA | CANCELADA |
| PENDIENTE_PAGO vencida | 409 "venció" | CANCELADA |
| CONFIRMADA | sin cambios (idempotente: el futuro pagos-service puede reintentar) | CANCELADA |
| CANCELADA | 409 | sin cambios |
| EXPIRADA | 409 | 409 |

- [ ] **Step 1: Tests que fallan** — `ReservaTest`: un test por cada celda de las tres primeras filas y la de CANCELADA (EXPIRADA sólo la produce el job; se prueba en Task 9). `ReservaApiIntegrationTest`:

```java
confirmar_pendiente_200Confirmada
confirmar_vencidaAunqueElJobNoCorrio_409   // reloj.avanzar(10 min) -> 409, detail contiene "venció"
confirmar_cancelada_409
confirmar_inexistente_404
cancelar_200Cancelada_yElTurnoQuedaLibre   // luego POST del mismo turno -> 201
```

- [ ] **Step 2:** `.\mvnw.cmd test -Dtest="ReservaTest,ReservaApiIntegrationTest"` → FAIL (no compila).
- [ ] **Step 3:** Implementar la tabla en la entidad (lanza `ConflictoException`) y los dos métodos del service (`findById` → 404, transición, `ReservaResponse.desde`).
- [ ] **Step 4:** mismo comando → PASS.

### Task 9: Expiración programada

**Files:** Create `reserva/ExpiracionDeReservas.java`; Modify `reserva/ReservaRepository.java`, `ReservasServiceApplication.java` (`@EnableScheduling`); Test `reserva/ExpiracionDeReservasIntegrationTest.java`

**Interfaces:**
- `ReservaRepository`: `@Modifying @Query("update Reserva r set r.estado = :expirada, r.version = r.version + 1 where r.estado = :pendiente and r.venceEn <= :ahora") int expirarVencidas(Instant ahora, EstadoReserva pendiente, EstadoReserva expirada)`. Sube `version` para que una confirmación que leyó la fila antes falle por locking optimista (409) en vez de pisar el EXPIRADA.
- `ExpiracionDeReservas` (`@Component`): `@Scheduled(cron = "${reservas.expiracion.cron}") @Transactional public int expirarVencidas()`; loguea en INFO cuántas expiró si son > 0. Es idempotente: con varias instancias, la segunda actualiza 0 filas (no hace falta ShedLock por ahora).

- [ ] **Step 1: Tests que fallan**

```java
expirarVencidas_soloLasVencidas      // crear A (vence 12:10); avanzar 5 min; crear B 19:00 (vence 12:15); avanzar 6 min
                                     // -> devuelve 1; A EXPIRADA; B PENDIENTE_PAGO
expirarVencidas_liberaElTurno        // tras expirar A, POST del mismo turno -> 201
expirarVencidas_noTocaConfirmadas    // confirmar A; avanzar 11 min -> devuelve 0
confirmar_expirada_409
expirarVencidas_subeLaVersion        // leer A (copia suelta); avanzar 11 min; job; a.cancelar(); save(a)
                                     // -> assertThrows(ObjectOptimisticLockingFailureException.class)
```

- [ ] **Step 2:** `.\mvnw.cmd test -Dtest=ExpiracionDeReservasIntegrationTest` → FAIL (no compila).
- [ ] **Step 3:** Implementar repositorio + componente + `@EnableScheduling`.
- [ ] **Step 4:** mismo comando → PASS.

### Task 10: Verificación final

- [ ] **Step 1:** `.\mvnw.cmd test` → `BUILD SUCCESS`, 0 fallos. Revisar que no haya stack traces inesperados en la salida.
- [ ] **Step 2:** Levantar canchas-service y reservas-service con `.\mvnw.cmd spring-boot:run`: `/actuator/health` UP; `GET /api/canchas/{id}/disponibilidad` de una cancha real; `POST /api/reservas` → 201; `POST .../confirmar` → 200; `/swagger-ui/index.html` muestra los tags "Reservas" y "Disponibilidad". Frenar canchas-service → `POST /api/reservas` → 503 en ≤ 3 s. Frenar ambos.
