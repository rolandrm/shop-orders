package com.shop.orders.platform.web;

import com.shop.orders.sharedkernel.DomainError;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Convertit toute exception en {@link ApiError}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(DomainErrorException.class)
  ResponseEntity<ApiError> handleDomain(DomainErrorException ex) {
    DomainError error = ex.error();
    HttpStatus status =
        switch (error.kind()) {
          case VALIDATION -> HttpStatus.BAD_REQUEST;
          case NOT_FOUND -> HttpStatus.NOT_FOUND;
          case CONFLICT -> HttpStatus.CONFLICT;
          case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
          case UNEXPECTED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    return build(status, error.code(), error.message(), List.of());
  }

  /** Rejets de requête (en-tête, identifiant, corps, paramètre) : 400, message fixe. */
  @ExceptionHandler({
    MissingRequestHeaderException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    HandlerMethodValidationException.class
  })
  ResponseEntity<ApiError> handleBadRequest(Exception ex) {
    return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Requête invalide", List.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
    List<FieldError> errors = ex.getBindingResult().getFieldErrors();
    List<Map<String, String>> details = errors.stream().map(e -> detail(e)).toList();
    return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Requête invalide", details);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> handleOther(Exception ex) {
    if (ex instanceof ErrorResponse errorResponse) {
      HttpStatusCode code = errorResponse.getStatusCode();
      HttpStatus status = HttpStatus.resolve(code.value());
      String name = status == null ? "ERROR" : status.name();
      String message = status == null ? "Erreur" : status.getReasonPhrase();
      return build(code, name, message, List.of());
    }
    LOG.error("Unexpected error", ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Erreur interne", List.of());
  }

  private static Map<String, String> detail(FieldError error) {
    String message = error.getDefaultMessage();
    return Map.of("field", error.getField(), "message", message == null ? "invalid" : message);
  }

  private static ResponseEntity<ApiError> build(
      HttpStatusCode status, String code, String message, List<?> details) {
    String traceId = MDC.get(RequestIdFilter.MDC_KEY);
    ApiError body =
        new ApiError(status.value(), code, message, details, traceId == null ? "" : traceId);
    return ResponseEntity.status(status).body(body);
  }
}
