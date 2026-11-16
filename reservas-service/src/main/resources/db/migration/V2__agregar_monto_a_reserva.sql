-- Precio de la cancha (precioHora de canchas-service) al momento de reservar. Se guarda en la
-- reserva para que un cambio de precio posterior no modifique lo que el cliente tiene que pagar.
-- NOT NULL sin DEFAULT: requiere que la tabla esté vacía o que se completen las filas antes.
ALTER TABLE reserva ADD COLUMN monto NUMERIC(10, 2) NOT NULL;
