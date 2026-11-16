-- Pagos de reservas con Mercado Pago (API de Orders).
-- reserva_id apunta a reservas-service, que tiene su propia base: no hay FK entre servicios.
CREATE TABLE pago (
    id                 BIGSERIAL      PRIMARY KEY,
    reserva_id         BIGINT         NOT NULL,
    monto              NUMERIC(10, 2) NOT NULL CHECK (monto > 0),
    estado             VARCHAR(20)    NOT NULL
        CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'EXPIRADO', 'REEMBOLSO_PENDIENTE', 'REEMBOLSADO')),
    -- Se completa después de crear la order (la order necesita el id del pago como external_reference).
    mp_order_id        VARCHAR(64)    UNIQUE,
    checkout_url       VARCHAR(500),
    -- X-Idempotency-Key de la creación de la order: si el pedido se reintenta, Mercado Pago
    -- devuelve la misma order en vez de crear otra.
    clave_idempotencia UUID           NOT NULL UNIQUE,
    creado_en          TIMESTAMPTZ    NOT NULL,
    actualizado_en     TIMESTAMPTZ    NOT NULL,
    version            BIGINT         NOT NULL DEFAULT 0
);

-- Un solo pago en curso o aprobado por reserva: dos pedidos simultáneos no pueden crear dos
-- orders (y cobrar dos veces) para la misma reserva. Los rechazados, expirados y reembolsados
-- no cuentan.
CREATE UNIQUE INDEX ux_pago_reserva_activo ON pago (reserva_id) WHERE estado IN ('PENDIENTE', 'APROBADO');

-- Lo recorre la tarea que reintenta los reembolsos.
CREATE INDEX ix_pago_reembolso_pendiente ON pago (id) WHERE estado = 'REEMBOLSO_PENDIENTE';

-- Outbox: el evento se guarda en la misma transacción que el cambio de estado y una tarea
-- programada lo publica en RabbitMQ. Así no se pierde un evento si RabbitMQ está caído, ni se
-- publica uno cuyo cambio de estado después hizo rollback.
CREATE TABLE evento_saliente (
    id          UUID        PRIMARY KEY,
    tipo        VARCHAR(50) NOT NULL,
    payload     JSONB       NOT NULL,
    ocurrido_en TIMESTAMPTZ NOT NULL,
    enviado_en  TIMESTAMPTZ
);

CREATE INDEX ix_evento_saliente_pendiente ON evento_saliente (ocurrido_en) WHERE enviado_en IS NULL;

-- Inbox: RabbitMQ entrega "al menos una vez"; un evento cuyo id ya está acá se ignora.
CREATE TABLE evento_procesado (
    evento_id    UUID        PRIMARY KEY,
    tipo         VARCHAR(50) NOT NULL,
    procesado_en TIMESTAMPTZ NOT NULL
);
