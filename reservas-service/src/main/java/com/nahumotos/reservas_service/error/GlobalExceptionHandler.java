package com.nahumotos.reservas_service.error;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * Traduce las excepciones a respuestas RFC 9457 (ProblemDetail). Mismo criterio que
 * canchas-service, más los casos propios de reservas: 409 por conflictos de estado,
 * 400 por reglas de turnos y 503 cuando canchas-service no responde.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	public record CampoInvalido(String campo, String mensaje) {
	}

	@ExceptionHandler(RecursoNoEncontradoException.class)
	ProblemDetail manejarNoEncontrado(RecursoNoEncontradoException ex) {
		return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
	}

	@ExceptionHandler(ConflictoException.class)
	ProblemDetail manejarConflicto(ConflictoException ex) {
		return problema(HttpStatus.CONFLICT, "Conflicto", ex.getMessage());
	}

	@ExceptionHandler(SolicitudInvalidaException.class)
	ProblemDetail manejarSolicitudInvalida(SolicitudInvalidaException ex) {
		return problema(HttpStatus.BAD_REQUEST, "Solicitud inválida", ex.getMessage());
	}

	@ExceptionHandler(ServicioNoDisponibleException.class)
	ProblemDetail manejarServicioNoDisponible(ServicioNoDisponibleException ex) {
		// La causa (timeout, conexión rechazada, 5xx) queda en el log, no en la respuesta.
		log.warn("Dependencia no disponible: {}", ex.getMessage(), ex.getCause());
		return problema(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
				"No pudimos consultar las canchas en este momento. Probá de nuevo en unos segundos.");
	}

	/** Otro pedido (o la tarea de expiración) modificó la reserva entre que la leímos y la guardamos. */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail manejarModificacionConcurrente(ObjectOptimisticLockingFailureException ex) {
		return problema(HttpStatus.CONFLICT, "Conflicto",
				"La reserva cambió mientras se procesaba el pedido; consultala y volvé a intentar");
	}

	/** Bean Validation sobre el body: un error por cada campo que no cumple sus restricciones. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		// Hibernate Validator no garantiza orden; ordenamos para que la respuesta sea estable.
		List<CampoInvalido> errores = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new CampoInvalido(error.getField(), error.getDefaultMessage()))
				.sorted(Comparator.comparing(CampoInvalido::campo).thenComparing(CampoInvalido::mensaje))
				.toList();
		return handleExceptionInternal(ex, solicitudInvalida(errores), headers, status, request);
	}

	/**
	 * Body que Jackson no puede convertir. Si el problema está en un campo concreto
	 * (p. ej. {@code "fecha": "11-06-2030"}) se informa igual que un error de validación;
	 * si el JSON está roto, se devuelve un 400 genérico.
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problema;
		if (ex.getCause() instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
			problema = solicitudInvalida(List.of(new CampoInvalido(rutaDelCampo(jackson), describir(jackson))));
		}
		else {
			problema = problema(HttpStatus.BAD_REQUEST, "Solicitud inválida", "El cuerpo de la solicitud no es un JSON válido");
		}
		return handleExceptionInternal(ex, problema, headers, status, request);
	}

	private static ProblemDetail problema(HttpStatus status, String titulo, String detalle) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
		problema.setTitle(titulo);
		return problema;
	}

	private static ProblemDetail solicitudInvalida(List<CampoInvalido> errores) {
		ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Solicitud inválida", "Uno o más campos son inválidos");
		problema.setProperty("errores", errores);
		return problema;
	}

	private static String rutaDelCampo(JacksonException ex) {
		return ex.getPath().stream()
				.map(ref -> ref.getPropertyName() != null ? ref.getPropertyName() : "[" + ref.getIndex() + "]")
				.collect(Collectors.joining("."));
	}

	private static String describir(JacksonException ex) {
		if (ex instanceof InvalidFormatException invalido && invalido.getTargetType() != null
				&& invalido.getTargetType().isEnum()) {
			return "valor inválido; valores permitidos: " + Arrays.toString(invalido.getTargetType().getEnumConstants());
		}
		return "valor inválido o con formato incorrecto";
	}

}
