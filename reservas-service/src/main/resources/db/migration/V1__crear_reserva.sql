-- cancha_id y club_id no tienen FK: canchas y clubes viven en la base de canchas-service.
CREATE TABLE reserva (
    id             BIGSERIAL PRIMARY KEY,
    cancha_id      BIGINT       NOT NULL,
    club_id        BIGINT       NOT NULL,
    fecha          DATE         NOT NULL,
    hora_inicio    TIME         NOT NULL,
    -- Para el turno de 23:00 es 00:00, o sea menor que hora_inicio.
    hora_fin       TIME         NOT NULL,
    cliente_nombre VARCHAR(100) NOT NULL,
    cliente_email  VARCHAR(200) NOT NULL,
    estado         VARCHAR(20)  NOT NULL
        CHECK (estado IN ('PENDIENTE_PAGO', 'CONFIRMADA', 'CANCELADA', 'EXPIRADA')),
    vence_en       TIMESTAMPTZ  NOT NULL,
    creada_en      TIMESTAMPTZ  NOT NULL,
    -- Locking optimista (@Version).
    version        BIGINT       NOT NULL DEFAULT 0
);

-- Un turno (cancha + fecha + hora) sólo puede tener una reserva viva. Es parcial para que
-- las reservas canceladas o expiradas no bloqueen el turno. Lo garantiza la base, así que
-- dos pedidos simultáneos no pueden crear dos reservas aunque ambos pasen las validaciones.
CREATE UNIQUE INDEX ux_reserva_turno_activo
    ON reserva (cancha_id, fecha, hora_inicio)
    WHERE estado IN ('PENDIENTE_PAGO', 'CONFIRMADA');

-- GET /api/reservas?clienteEmail=...
CREATE INDEX ix_reserva_cliente_email ON reserva (cliente_email);

-- La tarea de expiración busca pendientes vencidas.
CREATE INDEX ix_reserva_pendiente_vence_en ON reserva (vence_en) WHERE estado = 'PENDIENTE_PAGO';
