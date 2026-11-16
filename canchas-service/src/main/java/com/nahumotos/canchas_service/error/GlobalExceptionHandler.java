package com.nahumotos.canchas_service.error;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * Traduce las excepciones a respuestas RFC 9457 (ProblemDetail).
 * <p>
 * Extiende {@link ResponseEntityExceptionHandler} para que los errores propios de
 * Spring MVC (JSON mal formado, tipo de path variable inválido, método no soportado...)
 * también salgan como ProblemDetail, y sobrescribe los casos de 400 para listar
 * cada campo inválido en la propiedad {@code errores}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	public record CampoInvalido(String campo, String mensaje) {
	}

	@ExceptionHandler(RecursoNoEncontradoException.class)
	ProblemDetail manejarNoEncontrado(RecursoNoEncontradoException ex) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problema.setTitle("Recurso no encontrado");
		return problema;
	}

	@ExceptionHandler(RecursoDuplicadoException.class)
	ProblemDetail manejarDuplicado(RecursoDuplicadoException ex) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		problema.setTitle("Recurso duplicado");
		return problema;
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
	 * (p. ej. {@code "deporte": "TENIS"} o {@code "horaApertura": "25:00"}) se informa
	 * igual que un error de validación; si el JSON está roto, se devuelve un 400 genérico.
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problema;
		if (ex.getCause() instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
			problema = solicitudInvalida(List.of(new CampoInvalido(rutaDelCampo(jackson), describir(jackson))));
		}
		else {
			problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es un JSON válido");
			problema.setTitle("Solicitud inválida");
		}
		return handleExceptionInternal(ex, problema, headers, status, request);
	}

	private static ProblemDetail solicitudInvalida(List<CampoInvalido> errores) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Uno o más campos son inválidos");
		problema.setTitle("Solicitud inválida");
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
