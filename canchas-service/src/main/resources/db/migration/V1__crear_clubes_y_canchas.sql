CREATE TABLE club (
    id            BIGSERIAL PRIMARY KEY,
    nombre        VARCHAR(100) NOT NULL,
    direccion     VARCHAR(200) NOT NULL,
    hora_apertura TIME NOT NULL,
    hora_cierre   TIME NOT NULL
);

CREATE TABLE cancha (
    id          BIGSERIAL PRIMARY KEY,
    club_id     BIGINT NOT NULL REFERENCES club(id),
    nombre      VARCHAR(50) NOT NULL,
    deporte     VARCHAR(20) NOT NULL CHECK (deporte IN ('FUTBOL5', 'FUTBOL7', 'PADEL')),
    precio_hora NUMERIC(10, 2) NOT NULL,
    activa      BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (club_id, nombre)
);