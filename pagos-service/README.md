# pagos-service

Cobra las reservas con Mercado Pago Checkout Pro (API de Orders) y participa de la saga con
reservas-service por RabbitMQ. Puerto 8084, base `pagos`.

## Flujo

1. `POST /api/pagos {"reservaId": 1}` crea la order y devuelve el `checkoutUrl`.
2. El cliente paga en el checkout de Mercado Pago.
3. Mercado Pago llama al webhook. pagos-service consulta la order y, si quedó `processed`,
   publica `pago.aprobado`.
4. reservas-service confirma la reserva (`reserva.confirmada`). Si ya no puede (venció o se
   canceló), publica `reserva.rechazada` y pagos-service reembolsa.
5. Si se cancela una reserva confirmada con 24 h de anticipación o más, reservas-service
   publica `reserva.cancelada` y pagos-service reembolsa.

Una order que termina `processed` nunca se ignora. Si el pago estaba RECHAZADO o EXPIRADO,
pasa a APROBADO y reservas-service decide. Si la reserva ya tiene otro pago activo, el cobro
sobra y se reembolsa.

Si Mercado Pago falla al reembolsar, el pago queda `REEMBOLSO_PENDIENTE` y una tarea lo
reintenta cada minuto. Si responde con un error permanente (un 4xx que no es 409 ni 429), queda
`REEMBOLSO_FALLIDO` con un log ERROR y hay que revisarlo a mano.

`GET /api/pagos/{id}` muestra el estado del pago. La documentación está en Swagger:
http://localhost:8084/swagger-ui.html

## Variables

Se leen del `.env` de la raíz del repo (`spring.config.import`) o de variables de entorno.
`.env.example` tiene la lista.

| Variable | Para qué | Valor por defecto |
|---|---|---|
| `MP_ACCESS_TOKEN` | Access Token de **prueba** de tu aplicación (Tus integraciones > Credenciales de prueba) | ninguno: sin él el servicio no arranca |
| `MP_WEBHOOK_SECRET` | Firma secreta de los webhooks (paso 4 de abajo) | ninguno: sin ella el servicio no arranca |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | RabbitMQ del docker-compose | `app` / `cambiame` |
| `DB_USER` / `DB_PASSWORD` | PostgreSQL del docker-compose | `app` / `cambiame` |
| `RESERVAS_SERVICE_URL` | Dónde está reservas-service | `http://localhost:8082` |

No hay URLs de "sandbox". La API es la misma; lo que hace que todo sea de prueba es usar el
Access Token de prueba y pagar con la cuenta compradora de prueba.

## Levantarlo

```powershell
docker compose up -d                 # desde la raíz: PostgreSQL y RabbitMQ
cd pagos-service
.\mvnw.cmd spring-boot:run
```

Cada servicio declara en RabbitMQ las colas que consume. Los eventos salen de una tabla outbox
y se publican con `mandatory`. Si la cola del consumidor todavía no existe (porque el otro
servicio nunca arrancó), el evento queda pendiente, aparece un WARN por minuto y sale solo
cuando la cola existe. La excepción es `reserva.confirmada`: nadie la consume todavía, así que se
publica sin `mandatory`.

RabbitMQ guarda sus datos en el volumen `rabbitmqdata` (con `hostname: rabbitmq` fijo), así que
colas y mensajes sobreviven a un `docker compose down`.

## Webhook con ngrok

Mercado Pago necesita una URL pública con HTTPS para avisar los pagos.

1. Instalá ngrok. La primera vez, registrá tu token (lo ves en el dashboard de ngrok, en
   *Your Authtoken*):
   ```powershell
   ngrok config add-authtoken <tu-token>
   ```
   Con pagos-service corriendo, abrí el túnel:
   ```powershell
   ngrok http 8084
   ```
   Copiá la URL `https://....ngrok-free.app` que muestra.
2. En [Tus integraciones](https://www.mercadopago.com.ar/developers/panel/app) elegí la
   aplicación, entrá a **Webhooks > Configurar notificaciones** y elegí el modo **Prueba**.
3. URL: `https://<tu-subdominio>.ngrok-free.app/api/pagos/webhooks/mercadopago`.
   Evento: **Order (Mercado Pago)**. Guardá.
4. Mercado Pago genera la **firma secreta**. Copiala en `MP_WEBHOOK_SECRET` del `.env` y
   reiniciá pagos-service.
5. Con **Simular notificación** podés probar que llega. Va a responder 200 aunque la order no
   exista, porque las orders ajenas se ignoran.

La URL de ngrok cambia cada vez que lo reiniciás (en el plan gratis), así que hay que volver al
paso 3.

## Probar un pago

1. Reservá un turno en reservas-service (`POST /api/reservas`). Queda `PENDIENTE_PAGO` por 10 minutos.
2. `POST /api/pagos {"reservaId": <id>}` y abrí el `checkoutUrl` en una ventana de incógnito.
3. Iniciá sesión con la **cuenta compradora de prueba** y pagá con una tarjeta de prueba.
4. En unos segundos `GET /api/reservas/<id>` muestra `CONFIRMADA` y `GET /api/pagos/<pagoId>`
   muestra `APROBADO`.

Si Mercado Pago responde 401 `invalid_credentials`, puede ser por lo que dicen las referencias
de "obtener order" y "reembolsar order": las credenciales de prueba no serían compatibles y habría
que usar las credenciales de **producción del usuario vendedor de prueba**.

## Limitaciones conocidas

- No se valida la antigüedad de `ts` en la firma del webhook. No hace falta, porque el estado
  siempre se toma consultando la order de nuevo, nunca del body.
- No se limpian las tablas `evento_saliente` ni `evento_procesado`.
- No hay herramienta para reprocesar los mensajes de las DLQ (`*.dlq`).
- Un reembolso hecho a mano desde el panel de Mercado Pago pasa el pago a `REEMBOLSADO`, pero
  la reserva queda `CONFIRMADA`.
- Falta confirmar en la prueba real que Mercado Pago acepta un `expiration_time` en minutos
  (`PT9M`). La documentación sólo muestra `P1D`.

## Tests

```powershell
.\mvnw.cmd test
```

Necesitan Docker: PostgreSQL 18 y RabbitMQ 4 corren en Testcontainers. No salen a internet.
Mercado Pago se simula con MockRestServiceServer y respuestas tomadas de la documentación
oficial.
