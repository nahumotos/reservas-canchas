# pagos-service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cobrar las reservas con Mercado Pago Checkout Pro (API de Orders, modo prueba) desde un nuevo `pagos-service`, y conectar pagos y reservas con una saga coreografiada sobre RabbitMQ: la reserva se confirma sola cuando se paga y, si no se puede confirmar o se cancela con anticipación, se reembolsa.

**Architecture:** `pagos-service` (8084, base `pagos`) crea la order con RestClient (sin SDK), recibe el webhook firmado de Mercado Pago, relee la order con `GET /v1/orders/{id}` y publica `pago.aprobado`. `reservas-service` lo consume, confirma o rechaza, y publica `reserva.confirmada` / `reserva.rechazada` / `reserva.cancelada`; pagos reembolsa ante las dos últimas. Los dos servicios usan outbox (`evento_saliente`, publicado por un @Scheduled con publisher confirms) e inbox (`evento_procesado`) en la misma transacción que el cambio de estado.

**Tech Stack:** Spring Boot 4.1.2, Java 25, Spring AMQP 4.1.2 (`JacksonJsonMessageConverter`, Jackson 3), Spring Data JPA + Flyway + PostgreSQL 18, springdoc 3.1.0, Testcontainers 2.0.5 (postgres:18, rabbitmq:4-management), MockRestServiceServer, Awaitility.

**Spec:** el pedido del usuario del 16/11/2026 (sección OBJETIVO a CIERRE), más la documentación oficial de Mercado Pago leída el 16/11/2026:
- create-order: `POST /v1/orders`, `type=online`, `processing_mode=manual`, `total_amount` es **string** ("1000.00"), `X-Idempotency-Key` obligatorio, `payer` opcional (si va, exige email), `items` opcional (si van, deben sumar `total_amount`), la respuesta trae `id`, `status=created` y `checkout_url`.
- payment-notifications: query `data.id` y `type=order`, headers `x-signature` (`ts=...,v1=...`) y `x-request-id`; responder 200/201 en menos de 22 s; reintenta a los 15 min, 30 min, 6 h, 48 h, 96 h…
- define-order-validity: `expiration_time` en duración ISO 8601 (la doc sólo muestra `P1D`; no documenta mínimo ni máximo).
- get-order: `GET /v1/orders/{id}`, 404 `order_not_found`.
- order-status (Checkout Pro): `created`, `processing`, `action_required`, `processed`, `failed`, `refunded`, `canceled` (la doc dice que una order vencida queda `canceled`; no existe `expired` en Checkout Pro, igual lo mapeamos por si aparece).
- refund-order: `POST /v1/orders/{id}/refund`, body vacío = reembolso total, `X-Idempotency-Key` obligatorio, 201 con `status=refunded`; 409 `order_already_refunded`, `idempotency_key_already_used`, `order_refund_already_in_process`.

## Global Constraints

- No tocar `canchas-service`. No commit ni push (el usuario revisa primero).
- Paquete `com.nahumotos.pagos_service`; mismas versiones que reservas-service (Boot 4.1.2, Java 25).
- Paquetes por funcionalidad, DTOs como records, ProblemDetail, Flyway + `ddl-auto: validate`, `Clock` inyectado, `TimeZone.setDefault(UTC)` en el main (y en el `static` de `TestcontainersConfiguration`), RestClient con timeouts, Testcontainers con `@ServiceConnection`, tests y comentarios en español explicando el porqué.
- Sin SDK de Mercado Pago, sin `sandbox_init_point` ni endpoints "sandbox": un solo `checkout_url`; lo que hace que sea prueba son las credenciales.
- No mandar el email del cliente como `payer`.
- Llamadas HTTP (Mercado Pago, reservas-service) siempre fuera de transacciones de base.
- Exchange topic `reservas-canchas.eventos`; routing keys `pago.aprobado`, `reserva.confirmada`, `reserva.rechazada`, `reserva.cancelada`; colas `reservas-service.pago-aprobado`, `pagos-service.reserva-rechazada`, `pagos-service.reserva-cancelada`, cada una con DLQ (`<cola>.dlq` vía exchange `reservas-canchas.eventos.dlx`) y reintentos limitados.
- Conversión con `JacksonJsonMessageConverter` (nunca `Jackson2JsonMessageConverter`); cada servicio tiene sus propios records; el tipo se infiere del parámetro del listener, no de `__TypeId__`.
- Todo evento lleva `eventoId` (UUID, también `messageId`), `tipo`, `ocurridoEn` y los datos.
- Secretos: `MP_ACCESS_TOKEN` y `MP_WEBHOOK_SECRET` sin valor por defecto, validados con `@Validated`; `spring.config.import: optional:file:../.env[.properties]`; nunca leer, mostrar ni logear `.env` ni tokens.
- Tests sin internet ni credenciales reales.

## Review Focus

1. **Notificación que llega dos veces (o en paralelo)** → un solo `pago.aprobado`. Lo cubre `@Version` + "sólo la transición PENDIENTE→APROBADO genera evento"; test en Task 6.
2. **Pago aprobado de una reserva ya vencida** (el job todavía no la marcó) → `reserva.rechazada` y reembolso, nunca CONFIRMADA. Test en Task 9.
3. **Mercado Pago caído al reembolsar** → el pago queda `REEMBOLSO_PENDIENTE` y el @Scheduled lo completa después; un 409 de MP se resuelve consultando la order. Tests en Task 7.
4. **Mensaje veneno** (JSON inválido) → tras los reintentos va a la DLQ y no bloquea la cola. Tests en Tasks 2 y 8.
5. **Credenciales faltantes o vacías** (`.env` copiado de `.env.example`) → el servicio no arranca y dice qué variable falta. Test en Task 1.

---

## File Structure

### pagos-service (nuevo)

```
pagos-service/
  pom.xml                                   (Initializr + springdoc 3.1.0 + awaitility test)
  README.md
  src/main/resources/application.yml
  src/main/resources/db/migration/V1__crear_pago_y_eventos.sql
  src/main/java/com/nahumotos/pagos_service/
    PagosServiceApplication.java            main con UTC, @EnableScheduling, @ConfigurationPropertiesScan, OpenAPI
    config/RelojConfig.java                 Clock.systemUTC()
    config/PagosProperties.java             pagos.margen-minimo-para-pagar
    error/GlobalExceptionHandler.java       400/401/404/409/503
    error/{ConflictoException, RecursoNoEncontradoException, ServicioNoDisponibleException, FirmaInvalidaException}.java
    reservas/{ReservasClient, ReservaRemota, ReservasServiceProperties, ReservasClientConfig}.java
    mercadopago/{MercadoPagoClient, MercadoPagoProperties, MercadoPagoClientConfig, NuevaOrder, OrderMp, FirmaDeMercadoPago}.java
    pago/{Pago, EstadoPago, EstadosDeOrder, PagoRepository, PagoService, PagoController, CrearPagoRequest, PagoResponse}.java
    webhook/{WebhookController, NotificacionesDeMercadoPago}.java
    reembolso/{Reembolsos, EscuchaDeReservas, ReintentoDeReembolsos}.java
    eventos/{Evento, PagoAprobado, ReservaRechazada, ReservaCancelada, EventoSaliente, EventoSalienteRepository,
             BandejaDeSalida, PublicadorDeEventos, BandejaDeEntrada, RabbitConfig}.java
  src/test/...                              (espejo de main + fixtures JSON de la doc en resources/mercadopago/)
```

### reservas-service (cambios)

```
pom.xml                                     + spring-boot-starter-amqp, -amqp-test, testcontainers-rabbitmq, awaitility
application.yml                             + spring.rabbitmq.*, eventos.publicacion.cron, reservas.cancelacion.anticipacion-minima
db/migration/V3__crear_eventos.sql          evento_saliente + evento_procesado
config/ReservasProperties.java              + Cancelacion(anticipacionMinima)
eventos/{Evento, PagoAprobado, ReservaConfirmada, ReservaRechazada, ReservaCancelada, EventoSaliente,
         EventoSalienteRepository, BandejaDeSalida, PublicadorDeEventos, BandejaDeEntrada, RabbitConfig}.java
reserva/ConfirmacionPorPago.java            @RabbitListener pago.aprobado → confirma o rechaza
reserva/Reserva.java                        + impedimentoParaConfirmar(Instant)
reserva/ReservaService.java                 - confirmar(Long); cancelar() con anticipación mínima + reserva.cancelada
reserva/ReservaController.java              - POST /{id}/confirmar
test: TestcontainersConfiguration (+RabbitMQ), PruebaDeIntegracion (limpia tablas de eventos, cron "-"),
      ReservaApiIntegrationTest y ExpiracionDeReservasIntegrationTest adaptados, tests nuevos de eventos.
```

---

### Task 1: pagos-service — base del proyecto, configuración y secretos

**Files:**
- Create: `pagos-service/` (Initializr: web, data-jpa, postgresql, flyway, validation, actuator, testcontainers, rabbitmq), `application.yml` (borrar `application.properties`), `PagosServiceApplication.java`, `config/*`, `mercadopago/MercadoPagoProperties.java`, `reservas/ReservasServiceProperties.java`, `error/*`, `V1__crear_pago_y_eventos.sql`
- Test: `PagosServiceApplicationTests.java`, `TestcontainersConfiguration.java` (postgres:18, rabbitmq:4-management, UTC en `static`), `PruebaDeIntegracion.java`, `RelojDeTest(Configuration).java`, `mercadopago/MercadoPagoPropertiesTest.java`

**Interfaces:**
- Produces: `MercadoPagoProperties(@NotNull URI urlBase, @NotBlank String accessToken, @NotBlank String webhookSecret, @NotNull Duration timeoutConexion, @NotNull Duration timeoutLectura)` con prefijo `mercadopago`; `ReservasServiceProperties(URI url, Duration timeoutConexion, Duration timeoutLectura)` prefijo `reservas-service`; `PagosProperties(@NotNull Duration margenMinimoParaPagar)` prefijo `pagos`.
- Produces: tablas `pago`, `evento_saliente(id UUID PK, tipo, payload JSONB, ocurrido_en, enviado_en NULL)`, `evento_procesado(evento_id UUID PK, tipo, procesado_en)`; índice único parcial `ux_pago_reserva_activo ON pago(reserva_id) WHERE estado IN ('PENDIENTE','APROBADO')`; `mp_order_id` y `clave_idempotencia` UNIQUE; CHECK de estado con `PENDIENTE, APROBADO, RECHAZADO, EXPIRADO, REEMBOLSO_PENDIENTE, REEMBOLSADO`.
- Produces: `ServicioNoDisponibleException(String servicio, String mensaje, Throwable causa)` → 503 con detail "No pudimos comunicarnos con {servicio}. Probá de nuevo en unos segundos."; `FirmaInvalidaException` → 401 "No autorizado".

- [ ] **Step 1:** Tests: `MercadoPagoPropertiesTest.sinAccessToken_noArranca` / `.webhookSecretVacio_noArranca` con `ApplicationContextRunner` (+`ValidationAutoConfiguration`): el contexto falla y el mensaje menciona `MP_ACCESS_TOKEN` / `MP_WEBHOOK_SECRET`. `contextLoads` con Testcontainers.
- [ ] **Step 2:** Correrlos: fallan (no existen las clases).
- [ ] **Step 3:** Implementar. `application.yml`: puerto 8084, `jdbc:postgresql://localhost:5432/pagos`, `spring.config.import: optional:file:../.env[.properties]`, `spring.rabbitmq` (`${RABBITMQ_USER:app}`/`${RABBITMQ_PASSWORD:cambiame}`, `publisher-confirm-type: correlated`, `listener.simple.default-requeue-rejected: false`, `retry.enabled: true`, `max-retries: 3`, `initial-interval: 1s`, `multiplier: 2`), `mercadopago.url-base: https://api.mercadopago.com`, `access-token: ${MP_ACCESS_TOKEN}`, `webhook-secret: ${MP_WEBHOOK_SECRET}` (verificar qué hace Boot con el placeholder sin resolver; si lo deja literal, la validación tiene que rechazarlo igual), timeouts 2s/5s, `reservas-service.url: ${RESERVAS_SERVICE_URL:http://localhost:8082}` 2s/3s, `pagos.margen-minimo-para-pagar: 1m`, `pagos.reembolsos.cron: "0 * * * * *"`, `eventos.publicacion.cron: "* * * * * *"`, springdoc igual que reservas.
- [ ] **Step 4:** Tests en verde (`.\mvnw.cmd test`).

### Task 2: pagos-service — eventos: outbox, inbox y topología RabbitMQ

**Files:**
- Create: `eventos/Evento.java`, `PagoAprobado.java`, `ReservaRechazada.java`, `ReservaCancelada.java`, `EventoSaliente.java`, `EventoSalienteRepository.java`, `BandejaDeSalida.java`, `PublicadorDeEventos.java`, `BandejaDeEntrada.java`, `RabbitConfig.java`
- Test: `eventos/PublicadorDeEventosIntegrationTest.java`, `eventos/BandejaDeEntradaIntegrationTest.java`, `eventos/DeadLetterIntegrationTest.java`

**Interfaces:**
- Produces: `interface Evento { UUID eventoId(); String tipo(); Instant ocurridoEn(); }`.
- Produces: `record PagoAprobado(UUID eventoId, String tipo, Instant ocurridoEn, Long pagoId, Long reservaId, BigDecimal monto, String mpOrderId)` con `static PagoAprobado de(Pago, Instant)` y `TIPO = "pago.aprobado"`; `record ReservaRechazada(UUID eventoId, String tipo, Instant ocurridoEn, Long reservaId, Long pagoId, String motivo)`; `record ReservaCancelada(UUID eventoId, String tipo, Instant ocurridoEn, Long reservaId)`. Los de entrada con `@JsonIgnoreProperties(ignoreUnknown = true)`.
- Produces: `BandejaDeSalida.guardar(Evento)` (exige transacción activa, `MANDATORY`; serializa con el `JsonMapper` de Boot y hace `persist`); `PublicadorDeEventos.publicarPendientes(): int` (@Scheduled `${eventos.publicacion.cron}`; `select … where enviado_en is null order by ocurrido_en limit 100 for update skip locked`; por cada uno `send` con `CorrelationData`, espera el confirm 5 s, si hay ack marca `enviado_en`, si no corta el lote); `BandejaDeEntrada.registrar(UUID eventoId, String tipo): boolean` (`insert … on conflict do nothing`, `MANDATORY`).
- Produces: `RabbitConfig.EXCHANGE = "reservas-canchas.eventos"`, `EXCHANGE_DLX = "reservas-canchas.eventos.dlx"`, `COLA_RESERVA_RECHAZADA = "pagos-service.reserva-rechazada"`, `COLA_RESERVA_CANCELADA = "pagos-service.reserva-cancelada"`; bean `Declarables` (exchange topic durable, DLX direct, colas durables con `x-dead-letter-exchange`/`x-dead-letter-routing-key = <cola>`, DLQs `<cola>.dlq`) y bean `MessageConverter` = `JacksonJsonMessageConverter(jsonMapper)` con `setAlwaysConvertToInferredType(true)`.
- Mensaje publicado: body = payload, `contentType=application/json`, `messageId=eventoId`, `type=tipo`, persistente, sin header `__TypeId__`.

- [ ] **Step 1:** Tests: `publicarPendientes_publicaConMessageIdYMarcaEnviado` (cola de prueba ligada a `pago.aprobado`; recibe body JSON con `eventoId`, `tipo`, `ocurridoEn`, `messageId == eventoId`, sin `__TypeId__`; segunda llamada devuelve 0); `registrar_dosVecesElMismoEvento_soloLaPrimeraEsNueva`; `mensajeInvalido_terminaEnLaDlq` (body `{no es json` a `reserva.rechazada` → aparece en `pagos-service.reserva-rechazada.dlq`).
- [ ] **Step 2:** Fallan.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Verde.

### Task 3: pagos-service — cliente de Mercado Pago y firma del webhook

**Files:**
- Create: `mercadopago/MercadoPagoClient.java`, `MercadoPagoClientConfig.java`, `NuevaOrder.java`, `OrderMp.java`, `FirmaDeMercadoPago.java`
- Test: `mercadopago/MercadoPagoClientTest.java`, `mercadopago/FirmaDeMercadoPagoTest.java`, fixtures `src/test/resources/mercadopago/{order-creada,order-procesada,order-reembolsada}.json` armados con la estructura de la doc.

**Interfaces:**
- Produces: `record NuevaOrder(String externalReference, BigDecimal monto, Duration vigencia, String descripcion)`; `record OrderMp(String id, String status, String statusDetail, String externalReference, BigDecimal totalAmount, String checkoutUrl)` (snake_case con `@JsonProperty`, ignora desconocidos).
- Produces: `MercadoPagoClient(RestClient, String accessToken)` con `OrderMp crearOrder(NuevaOrder, UUID claveIdempotencia)`, `Optional<OrderMp> obtenerOrder(String orderId)` (404 → vacío), `boolean reembolsar(String orderId, UUID claveIdempotencia)` (2xx → true, 409 → false). 5xx, 429, timeout o sin conexión → `ServicioNoDisponibleException("Mercado Pago", …)`; otros 4xx se propagan (bug o configuración nuestra).
- Body de la order: `type: online`, `processing_mode: manual`, `total_amount` string con 2 decimales, `external_reference`, `expiration_time` = `vigencia` en ISO 8601 (`PT9M`), `description`, un ítem `{title, unit_price, quantity: 1}` igual al total; sin `payer`, sin back URLs. Headers `Authorization: Bearer …`, `X-Idempotency-Key`.
- Produces: `FirmaDeMercadoPago(String secreto)` con `boolean esValida(String xSignature, String xRequestId, String dataId)`: separa por `,`, después por el primer `=`, toma `ts` y `v1`; manifest `id:{dataId en minúsculas};request-id:{xRequestId};ts:{ts};` omitiendo los pares sin valor; HMAC-SHA256 hex; `MessageDigest.isEqual`.

- [ ] **Step 1:** Tests de firma con un vector calculado afuera (openssl): válida, v1 alterado → false, header ausente → false, sin `ts` o sin `v1` → false, `data.id` en mayúsculas → válida, sin `x-request-id` (par omitido) → válida, espacios alrededor de las partes → válida. Tests del cliente: request exacto de `crearOrder` (método, URL, headers, JSON), parseo de la respuesta de la doc, `obtenerOrder` 200/404/500, `reembolsar` 201/409/500/timeout.
- [ ] **Step 2:** Fallan.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Verde.

### Task 4: pagos-service — cliente de reservas-service

**Files:** `reservas/ReservasClient.java`, `ReservaRemota.java`, `ReservasClientConfig.java`; test `reservas/ReservasClientTest.java`

**Interfaces:**
- Produces: `record ReservaRemota(Long id, BigDecimal monto, String estado, Instant venceEn)` (ignora desconocidos); `ReservasClient.obtenerReserva(Long id)`: 404 → `RecursoNoEncontradoException("No existe la reserva con id X")`, 5xx/timeout/sin conexión/sin cuerpo → `ServicioNoDisponibleException("reservas-service", …)`.

- [ ] **Step 1:** Tests 200 (con campos extra), 404, 500, sin conexión, timeout.
- [ ] **Step 2–4:** Fallan → implementar → verde.

### Task 5: pagos-service — crear y consultar pagos

**Files:** `pago/Pago.java`, `EstadoPago.java`, `PagoRepository.java`, `PagoService.java`, `PagoController.java`, `CrearPagoRequest.java`, `PagoResponse.java`; test `pago/PagoTest.java`, `pago/PagoApiIntegrationTest.java`

**Interfaces:**
- Produces: `Pago(Long reservaId, BigDecimal monto, UUID claveIdempotencia, Instant ahora)` en PENDIENTE; `asociarOrder(String mpOrderId, String checkoutUrl, Instant)`; `UUID claveDeReembolso()` = `UUID.nameUUIDFromBytes("reembolso:" + claveIdempotencia)` (fija por pago, sin columna extra).
- Produces: `PagoRepository.findFirstByReservaIdAndEstado(Long, EstadoPago)`, `findByEstado(EstadoPago)`.
- Produces: `POST /api/pagos {reservaId}` → 201 + `Location` (pago nuevo) o 200 (ya había uno PENDIENTE) con `PagoResponse(Long pagoId, Long reservaId, BigDecimal monto, EstadoPago estado, String checkoutUrl)`; `GET /api/pagos/{id}`.
- Flujo de `PagoService.crear`: reserva remota (404/503) → 409 si `estado != PENDIENTE_PAGO` o `venceEn - ahora < margenMinimoParaPagar` → transacción: buscar PENDIENTE; si no hay, insertar (violación de `ux_pago_reserva_activo` → recargar el PENDIENTE; si no hay, 409 "ya tiene un pago aprobado") → fuera de transacción, si el pago no tiene order: `crearOrder` con `vigencia = Duration.between(ahora, venceEn).truncatedTo(MINUTES)` y la clave guardada → transacción: `asociarOrder`.

- [ ] **Step 1:** Tests: 201 con `checkoutUrl` y `estado=PENDIENTE`, verificando que la `NuevaOrder` lleva `externalReference = pagoId`, monto de la reserva y `vigencia = PT9M` (reserva que vence en 9 min 30 s); 200 idempotente (mismo `pagoId`, una sola `crearOrder`); reserva inexistente 404; reservas-service caído 503; reserva CONFIRMADA 409; vence en 59 s 409; body sin `reservaId` 400; MP caído → 503 y un reintento posterior reutiliza el pago y la misma clave; `GET` 200/404.
- [ ] **Step 2–4:** Fallan → implementar → verde.

### Task 6: pagos-service — webhook de Mercado Pago

**Files:** `pago/EstadosDeOrder.java`, `webhook/WebhookController.java`, `webhook/NotificacionesDeMercadoPago.java`; métodos nuevos en `Pago`; tests `pago/PagoTest.java`, `webhook/WebhookIntegrationTest.java`

**Interfaces:**
- Produces: `EstadosDeOrder.estadoDePago(String status): Optional<EstadoPago>` (`processed`→APROBADO, `failed`→RECHAZADO, `expired`/`canceled`→EXPIRADO, `refunded`→REEMBOLSADO, otro → vacío).
- Produces: `Pago.registrarEstadoDeOrder(EstadoPago, Instant): boolean` (true sólo si pasó a APROBADO ahora). APROBADO/RECHAZADO/EXPIRADO sólo desde PENDIENTE; REEMBOLSADO desde PENDIENTE, APROBADO o REEMBOLSO_PENDIENTE; el resto no cambia.
- Produces: `POST /api/pagos/webhooks/mercadopago?data.id=…&type=…` con headers `x-signature`, `x-request-id`: firma inválida → 401; sin `data.id` o `type` distinto de `order` → 200; `obtenerOrder` (fuera de transacción; MP caído → 503 para que reintente); order inexistente o que no es nuestra (`external_reference` no es un pago, o el pago tiene otra `mp_order_id`) → 200; transacción: aplicar estado y, si quedó APROBADO, `BandejaDeSalida.guardar(PagoAprobado.de(...))`.

- [ ] **Step 1:** Tests: `PagoTest` con la tabla de transiciones. Integración: aprobado → pago APROBADO → `publicarPendientes()` → `pago.aprobado` en la cola de prueba con `pagoId`, `reservaId`, `monto`, `mpOrderId`; notificación repetida → 1 solo evento; firma inválida → 401 y nunca llama a MP; `failed` → RECHAZADO sin evento; `canceled` → EXPIRADO; order 404 → 200; order con `external_reference` ajeno → 200 sin cambios; MP caído → 503.
- [ ] **Step 2–4:** Fallan → implementar → verde.

### Task 7: pagos-service — reembolsos

**Files:** `reembolso/Reembolsos.java`, `EscuchaDeReservas.java`, `ReintentoDeReembolsos.java`; métodos `Pago.solicitarReembolso(Instant): boolean`, `Pago.marcarReembolsado(Instant)`; test `reembolso/ReembolsoIntegrationTest.java`

**Interfaces:**
- Produces: `EscuchaDeReservas.alRechazarse(ReservaRechazada)` (`@RabbitListener` en `pagos-service.reserva-rechazada`) y `alCancelarse(ReservaCancelada)` (`pagos-service.reserva-cancelada`): transacción {inbox (si es repetido, termina); buscar el pago (por `pagoId` o el APROBADO de `reservaId`); `solicitarReembolso` → REEMBOLSO_PENDIENTE}; después, fuera de la transacción, `Reembolsos.intentar(pagoId)`.
- Produces: `Reembolsos.intentar(Long pagoId)`: sólo si está en REEMBOLSO_PENDIENTE; `reembolsar(orderId, claveDeReembolso)`; si devuelve false (409) consulta la order y sólo si está `refunded` la da por reembolsada; éxito → REEMBOLSADO; cualquier falla → queda REEMBOLSO_PENDIENTE con log WARN (sin tokens).
- Produces: `ReintentoDeReembolsos.reintentar(): int` (@Scheduled `${pagos.reembolsos.cron}`) recorre los REEMBOLSO_PENDIENTE.

- [ ] **Step 1:** Tests: `reserva.rechazada` publicada dos veces con el mismo `eventoId` → `reembolsar` una sola vez y pago REEMBOLSADO; mismo pago rechazado con dos eventos distintos → igual un solo reembolso; MP caído → REEMBOLSO_PENDIENTE, después `reintentar()` → REEMBOLSADO; 409 + order `refunded` → REEMBOLSADO; `reserva.cancelada` → reembolsa el pago APROBADO de esa reserva; `reserva.cancelada` sin pago aprobado → no llama a MP.
- [ ] **Step 2–4:** Fallan → implementar → verde.

### Task 8: reservas-service — eventos: outbox, inbox y topología

**Files:** `pom.xml` (+amqp, amqp-test, testcontainers-rabbitmq, awaitility), `application.yml`, `V3__crear_eventos.sql`, `eventos/*` (mismo diseño que Task 2 con sus propios records: `PagoAprobado` de entrada; `ReservaConfirmada(eventoId, tipo, ocurridoEn, reservaId, pagoId)`, `ReservaRechazada(eventoId, tipo, ocurridoEn, reservaId, pagoId, motivo)`, `ReservaCancelada(eventoId, tipo, ocurridoEn, reservaId)` de salida; cola `reservas-service.pago-aprobado` ligada a `pago.aprobado`); test `TestcontainersConfiguration` (+`RabbitMQContainer("rabbitmq:4-management")`), `PruebaDeIntegracion` (borra `evento_saliente` y `evento_procesado`, `eventos.publicacion.cron=-`), `eventos/PublicadorDeEventosIntegrationTest.java`, `eventos/DeadLetterIntegrationTest.java`

- [ ] **Step 1:** Tests análogos a Task 2 (publicación con confirm y `messageId`, inbox, mensaje veneno → `reservas-service.pago-aprobado.dlq`).
- [ ] **Step 2–4:** Fallan → implementar → verde; la suite anterior sigue verde.

### Task 9: reservas-service — confirmación por `pago.aprobado` y fin de `/confirmar`

**Files:** `reserva/Reserva.java` (+`Optional<String> impedimentoParaConfirmar(Instant)`), `reserva/ConfirmacionPorPago.java`, `ReservaService.java` (−`confirmar`), `ReservaController.java` (−endpoint); tests `ReservaTest`, `reserva/ConfirmacionPorPagoIntegrationTest.java`, `ReservaApiIntegrationTest` y `ExpiracionDeReservasIntegrationTest` adaptados.

**Interfaces:**
- Produces: `impedimentoParaConfirmar(ahora)`: vacío si PENDIENTE_PAGO y no vencida; si no, el motivo ("La reserva venció antes de que se aprobara el pago", "La reserva expiró antes de que se aprobara el pago", "La reserva fue cancelada", "La reserva ya estaba confirmada").
- Produces: `ConfirmacionPorPago.alAprobarseElPago(PagoAprobado)` (`@RabbitListener` en `reservas-service.pago-aprobado`, `@Transactional`): inbox → reserva (inexistente → rechazada "La reserva no existe") → monto distinto → rechazada → impedimento → rechazada; si no, `confirmar(ahora)` (ya no puede lanzar: se decidió antes, así no queda la transacción rollback-only) + `reserva.confirmada`.

- [ ] **Step 1:** Tests: `pago.aprobado` por RabbitMQ → CONFIRMADA + `reserva.confirmada` publicada con `pagoId`; reserva vencida (sin job) → sigue PENDIENTE_PAGO + `reserva.rechazada` con motivo; cancelada → rechazada; inexistente → rechazada; mismo mensaje dos veces (y un tercero "marcador" para saber que ya se consumieron) → un solo evento de salida; `POST /api/reservas/{id}/confirmar` → 404. `ReservaTest` cubre los motivos.
- [ ] **Step 2–4:** Fallan → implementar → verde.

### Task 10: reservas-service — cancelar una CONFIRMADA

**Files:** `config/ReservasProperties.java` (+`@Valid @NotNull Cancelacion cancelacion`, `record Cancelacion(@NotNull Duration anticipacionMinima)`), `application.yml` (`reservas.cancelacion.anticipacion-minima: 24h`), `ReservaService.cancelar`, `ReservaController` (doc 409); test `reserva/CancelacionIntegrationTest.java`

- [ ] **Step 1:** Tests (hoy 10/06/2030 12:00): confirmada para el 11/06 18:00 (30 h) → 200 CANCELADA + `reserva.cancelada`; exactamente 24 h (11/06 12:00) → 200; 23 h (11/06 11:00) → 409 y sigue CONFIRMADA sin evento; PENDIENTE_PAGO → 200 sin evento.
- [ ] **Step 2–4:** Fallan → implementar → verde.

### Task 11: Cierre

- [ ] `.env.example` + `MP_ACCESS_TOKEN=` y `MP_WEBHOOK_SECRET=` (sin abrir `.env`).
- [ ] `pagos-service/README.md`: variables, `ngrok http 8084`, configuración del webhook (evento Order, URL `https://<ngrok>/api/pagos/webhooks/mercadopago`, firma secreta → `MP_WEBHOOK_SECRET`), cómo probar con la cuenta compradora de prueba.
- [ ] `.\mvnw.cmd test` en canchas-service, reservas-service y pagos-service: todo verde.
- [ ] Arranque de pagos-service sin credenciales → mensaje claro; con la infraestructura del docker-compose → health UP y Swagger.
- [ ] Sin commit ni push.

---

## Revisión del 16/11/2026 (aplicada antes del commit)

1. **Eventos que se pierden:** con publisher confirms RabbitMQ también da ack a un mensaje que no llegó a ninguna cola. El publicador ahora usa `mandatory` (`spring.rabbitmq.publisher-returns: true`). Si `CorrelationData.getReturned() != null`, el evento no se marca como enviado y se reintenta, con un WARN como mucho una vez por minuto por evento. `reserva.confirmada` va sin `mandatory` hasta que exista notificaciones-service. Hay tests en `PublicadorDeEventosIntegrationTest` de los dos servicios.
2. **Persistencia de RabbitMQ:** `docker-compose.yml` ahora tiene `hostname: rabbitmq` y el volumen `rabbitmqdata:/var/lib/rabbitmq`.
3. **Una order `processed` nunca se ignora:** un pago RECHAZADO o EXPIRADO pasa a APROBADO y publica `pago.aprobado`. Si choca con `ux_pago_reserva_activo`, pasa a REEMBOLSO_PENDIENTE y se reembolsa (`Pago.registrarCobroSobrante`). Hay tests en `PagoTest` y `WebhookIntegrationTest`.
4. **Estado `REEMBOLSO_FALLIDO`:** se agregó con la migración V2. Un 4xx que no es 409 ni 429 deja de reintentarse y se logea ERROR con pagoId y orderId.
5. **Finales de línea:** `.gitattributes` en la raíz (`* text=auto eol=lf`) y `git add --renormalize .`.
6. **canchas-service:** el `contextLoads` tiene ahora el bloque `static` con UTC.
7. **reservas-service:** usa `spring.config.import: optional:file:../.env[.properties]`.
8. **Limpieza:** se borró `pagos-service/HELP.md`. Las limitaciones que quedan están en el README de pagos-service.
