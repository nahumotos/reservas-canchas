-- Saga de pagos con RabbitMQ (pagos-service).

-- Outbox: el evento se guarda en la misma transacción que el cambio de estado de la reserva y
-- una tarea programada lo publica en RabbitMQ. Así no se pierde un evento si RabbitMQ está
-- caído, ni se publica uno cuyo cambio de estado después hizo rollback.
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
