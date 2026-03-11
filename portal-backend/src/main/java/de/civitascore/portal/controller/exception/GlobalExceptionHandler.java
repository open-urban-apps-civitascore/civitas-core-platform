package de.civitascore.portal.controller.exception;

import de.civitascore.portal.util.ExternalSystemRejectionException;
import de.civitascore.portal.util.ExternalSystemTimeoutException;
import de.civitascore.portal.util.ForbiddenException;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import jakarta.persistence.PersistenceException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final String ERROR_URN_PREFIX = "urn:civitas:error:";
  private static final Pattern UNIQUE_DETAIL_PATTERN =
      Pattern.compile("Key \\((.+?)\\)=\\((.+?)\\) already exists");

  private static final String UNIQUE_VIOLATION_STATE = "23505";
  private static final String FOREIGN_KEY_VIOLATION_STATE = "23503";
  private static final String NOT_NULL_VIOLATION_STATE = "23502";

  @ExceptionHandler(ResourceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
    log.warn("Resource not found: {}", ex.getMessage());
    return createProblemDetail(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler(InvalidInputException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ProblemDetail handleInvalidInput(InvalidInputException ex) {
    log.warn("Invalid input: {}", ex.getMessage());
    return createProblemDetail(HttpStatus.BAD_REQUEST, "INVALID_INPUT", ex.getMessage());
  }

  @ExceptionHandler(UniqueConstraintViolationException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ProblemDetail handleUniqueConstraint(UniqueConstraintViolationException ex) {
    log.warn("Unique constraint violation: {}", ex.getMessage());
    return createProblemDetail(HttpStatus.CONFLICT, "UNIQUE_CONSTRAINT_VIOLATION", ex.getMessage());
  }

  @ExceptionHandler(ResourceInUseException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ProblemDetail handleResourceInUse(ResourceInUseException ex) {
    log.warn("Resource in use: {}", ex.getMessage());
    return createProblemDetail(HttpStatus.CONFLICT, "RESOURCE_IN_USE", ex.getMessage());
  }

  @ExceptionHandler(ForbiddenException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ProblemDetail handleForbidden(ForbiddenException ex) {
    log.warn("Forbidden: {}", ex.getMessage());
    return createProblemDetail(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
    if (ex.getCause() instanceof ConstraintViolationException cve) {
      String sqlState = cve.getSQLState();

      if (UNIQUE_VIOLATION_STATE.equals(sqlState)) {
        String dbMessage = ex.getMostSpecificCause().getMessage();
        UniqueConstraintViolationException mapped = extractUniqueViolationException(dbMessage);
        log.warn("Unique constraint violation: {}", mapped.getMessage());
        return createProblemDetail(
            HttpStatus.CONFLICT, "UNIQUE_CONSTRAINT_VIOLATION", mapped.getMessage());
      }

      if (FOREIGN_KEY_VIOLATION_STATE.equals(sqlState)) {
        log.warn(
            "Foreign key violation on constraint '{}': {}",
            cve.getConstraintName(),
            cve.getMessage());
        return createProblemDetail(
            HttpStatus.CONFLICT,
            "FOREIGN_KEY_VIOLATION",
            "Referenced entity does not exist or is still in use");
      }

      if (NOT_NULL_VIOLATION_STATE.equals(sqlState)) {
        log.warn(
            "Not-null violation on constraint '{}': {}", cve.getConstraintName(), cve.getMessage());
        return createProblemDetail(
            HttpStatus.BAD_REQUEST, "NOT_NULL_VIOLATION", "A required field is missing");
      }
    }

    log.error("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
    return createProblemDetail(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "DATA_INTEGRITY_ERROR",
        "A data integrity error occurred");
  }

  @ExceptionHandler(PersistenceException.class)
  public ProblemDetail handlePersistenceException(PersistenceException ex) {
    if (ex.getCause() instanceof IllegalStateException ise) {
      log.warn("Entity validation failed: {}", ise.getMessage());
      return createProblemDetail(
          HttpStatus.BAD_REQUEST, "ENTITY_VALIDATION_FAILED", ise.getMessage());
    }
    log.error("Persistence error: {}", ex.getMessage(), ex);
    return createProblemDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "PERSISTENCE_ERROR", "A persistence error occurred");
  }

  @ExceptionHandler(ExternalSystemRejectionException.class)
  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  public ProblemDetail handleExternalSystemRejection(ExternalSystemRejectionException ex) {
    log.error("External system rejected request: {}", ex.getMessage());
    return createProblemDetail(
        HttpStatus.BAD_GATEWAY,
        "EXTERNAL_SYSTEM_ERROR",
        "The request was rejected by an external system");
  }

  @ExceptionHandler(ExternalSystemTimeoutException.class)
  @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
  public ProblemDetail handleExternalSystemTimeout(ExternalSystemTimeoutException ex) {
    log.error("External system timed out: {}", ex.getMessage());
    return createProblemDetail(
        HttpStatus.GATEWAY_TIMEOUT,
        "EXTERNAL_SYSTEM_TIMEOUT",
        "An external system did not respond in time");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                fe ->
                    Map.of(
                        "field",
                        fe.getField(),
                        "message",
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : ""))
            .toList();

    ProblemDetail problemDetail =
        createProblemDetail(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Input validation failed");
    problemDetail.setProperty("fieldErrors", fieldErrors);

    log.warn("Validation failed: {} field error(s)", fieldErrors.size());
    return ResponseEntity.badRequest().body(problemDetail);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    log.warn("Malformed request body: {}", ex.getMessage());
    ProblemDetail problemDetail =
        createProblemDetail(
            HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or malformed");
    return ResponseEntity.badRequest().body(problemDetail);
  }

  private ProblemDetail createProblemDetail(HttpStatus status, String errorCode, String detail) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setType(URI.create(ERROR_URN_PREFIX + errorCode));
    problemDetail.setTitle(status.getReasonPhrase());
    return problemDetail;
  }

  private UniqueConstraintViolationException extractUniqueViolationException(String dbMessage) {
    Matcher matcher = UNIQUE_DETAIL_PATTERN.matcher(dbMessage);
    if (matcher.find()) {
      return new UniqueConstraintViolationException(matcher.group(1), matcher.group(2));
    }
    return new UniqueConstraintViolationException();
  }
}
