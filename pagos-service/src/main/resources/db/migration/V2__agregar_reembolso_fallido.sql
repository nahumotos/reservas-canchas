-- Reembolsos que Mercado Pago rechaza con un error permanente (un 4xx que no es 409 ni 429):
-- dejan de reintentarse solos y quedan para revisar a mano.
-- pago_estado_check es el nombre que PostgreSQL le dio al CHECK de la columna en V1.
ALTER TABLE pago DROP CONSTRAINT pago_estado_check;

ALTER TABLE pago ADD CONSTRAINT pago_estado_check
    CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'EXPIRADO', 'REEMBOLSO_PENDIENTE', 'REEMBOLSO_FALLIDO',
                      'REEMBOLSADO'));
