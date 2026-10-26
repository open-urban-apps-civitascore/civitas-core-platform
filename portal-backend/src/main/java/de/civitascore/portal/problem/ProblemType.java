package de.civitascore.portal.problem;

import java.net.URI;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * The catalog of every error answer of portal-backend. Each constant fixes the HTTP status and the
 * type URN {@code urn:civitas:error:<name>} of an RFC 9457 problem. portal-frontend reads the URNs,
 * so the name of a constant is part of the API. Title and detail of each constant are in the
 * message bundle, see {@link ProblemDetails}.
 */
public enum ProblemType {
  INVALID_INPUT(HttpStatus.BAD_REQUEST),
  MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
  DATASET_NOT_EDITABLE(HttpStatus.BAD_REQUEST),
  MODEL_ROOT_MISSING(HttpStatus.BAD_REQUEST),
  MODEL_REFERENCE_NOT_AVAILABLE(HttpStatus.BAD_REQUEST),
  MODEL_MEMBER_UNREACHABLE(HttpStatus.BAD_REQUEST),
  NOT_NULL_VIOLATION(HttpStatus.BAD_REQUEST),
  ENTITY_VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED),
  ACCESS_DENIED(HttpStatus.FORBIDDEN),
  FORBIDDEN(HttpStatus.FORBIDDEN),
  NOT_FOUND(HttpStatus.NOT_FOUND),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
  NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE),
  UNIQUE_CONSTRAINT_VIOLATION(HttpStatus.CONFLICT),
  FOREIGN_KEY_VIOLATION(HttpStatus.CONFLICT),
  RESOURCE_IN_USE(HttpStatus.CONFLICT),
  REMOVAL_NOT_CONFIRMED(HttpStatus.CONFLICT),
  SAGA_IN_FLIGHT(HttpStatus.CONFLICT),
  MODEL_MISSING(HttpStatus.CONFLICT),
  PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
  DATASOURCE_SCOPE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY),
  PIPELINE_CLOSURE_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
  TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
  DATA_INTEGRITY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
  PERSISTENCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
  EXTERNAL_SYSTEM_ERROR(HttpStatus.BAD_GATEWAY),
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
  REGISTRY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
  EXTERNAL_SYSTEM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT);

  private static final String URN_PREFIX = "urn:civitas:error:";

  private final HttpStatus status;

  ProblemType(HttpStatus status) {
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }

  public URI type() {
    return URI.create(URN_PREFIX + name());
  }

  /**
   * The problem type for an error that Spring MVC raises itself, which it answers with type {@code
   * about:blank}. Empty for a status that no framework error of this application produces.
   */
  public static Optional<ProblemType> forFrameworkException(Exception ex, HttpStatusCode status) {
    if (ex instanceof HttpMessageNotReadableException) {
      return Optional.of(MALFORMED_REQUEST);
    }
    if (ex instanceof HandlerMethodValidationException && status.value() == 400) {
      return Optional.of(VALIDATION_FAILED);
    }
    return switch (status.value()) {
      case 400 -> Optional.of(INVALID_INPUT);
      case 404 -> Optional.of(NOT_FOUND);
      case 405 -> Optional.of(METHOD_NOT_ALLOWED);
      case 406 -> Optional.of(NOT_ACCEPTABLE);
      case 413 -> Optional.of(PAYLOAD_TOO_LARGE);
      case 415 -> Optional.of(UNSUPPORTED_MEDIA_TYPE);
      case 503 -> Optional.of(SERVICE_UNAVAILABLE);
      default -> status.is5xxServerError() ? Optional.of(INTERNAL_ERROR) : Optional.empty();
    };
  }
}
