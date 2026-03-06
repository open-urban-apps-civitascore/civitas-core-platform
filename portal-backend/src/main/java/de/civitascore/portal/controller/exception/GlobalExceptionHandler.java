package de.civitascore.portal.controller.exception;

import de.civitascore.portal.util.ExternalSystemRejectionException;
import de.civitascore.portal.util.ExternalSystemTimeoutException;
import de.civitascore.portal.util.ForbiddenException;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import jakarta.persistence.PersistenceException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Pattern UNIQUE_DETAIL_PATTERN =
      Pattern.compile("Key \\((.+?)\\)=\\((.+?)\\) already exists");

  private static final String UNIQUE_VIOLATION_STATE = "23505";
  private static final String FOREIGN_KEY_VIOLATION_STATE = "23503";
  private static final String NOT_NULL_VIOLATION_STATE = "23502";

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
    log.warn("Resource not found: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(createErrorMap("NOT_FOUND", ex.getMessage()));
  }

  @ExceptionHandler(InvalidInputException.class)
  public ResponseEntity<Map<String, Object>> handleInvalidInput(InvalidInputException ex) {
    log.warn("Invalid input: {}", ex.getMessage());
    return ResponseEntity.badRequest().body(createErrorMap("INVALID_INPUT", ex.getMessage()));
  }

  @ExceptionHandler(UniqueConstraintViolationException.class)
  public ResponseEntity<Map<String, Object>> handleUniqueConstraint(
      UniqueConstraintViolationException ex) {
    log.warn("Unique constraint violation: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(createErrorMap("UNIQUE_CONSTRAINT_VIOLATION", ex.getMessage()));
  }

  @ExceptionHandler(ResourceInUseException.class)
  public ResponseEntity<Map<String, Object>> handleResourceInUse(ResourceInUseException ex) {
    log.warn("Resource in use: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(createErrorMap("RESOURCE_IN_USE", ex.getMessage()));
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
    log.warn("Forbidden: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(createErrorMap("FORBIDDEN", ex.getMessage()));
  }

  // --- Database / persistence exceptions ---

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
      DataIntegrityViolationException ex) {

    if (ex.getCause() instanceof ConstraintViolationException cve) {
      String sqlState = cve.getSQLState();

      if (UNIQUE_VIOLATION_STATE.equals(sqlState)) {
        String dbMessage = ex.getMostSpecificCause().getMessage();
        UniqueConstraintViolationException mapped = extractUniqueViolationException(dbMessage);
        log.warn("Unique constraint violation: {}", mapped.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(createErrorMap("UNIQUE_CONSTRAINT_VIOLATION", mapped.getMessage()));
      }

      if (FOREIGN_KEY_VIOLATION_STATE.equals(sqlState)) {
        log.warn(
            "Foreign key violation on constraint '{}': {}",
            cve.getConstraintName(),
            cve.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(
                createErrorMap(
                    "FOREIGN_KEY_VIOLATION",
                    "Referenced entity does not exist or is still in use"));
      }

      if (NOT_NULL_VIOLATION_STATE.equals(sqlState)) {
        log.warn(
            "Not-null violation on constraint '{}': {}", cve.getConstraintName(), cve.getMessage());
        return ResponseEntity.badRequest()
            .body(createErrorMap("NOT_NULL_VIOLATION", "A required field is missing"));
      }
    }

    log.error("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(createErrorMap("DATA_INTEGRITY_ERROR", "A data integrity error occurred"));
  }

  @ExceptionHandler(PersistenceException.class)
  public ResponseEntity<Map<String, Object>> handlePersistenceException(PersistenceException ex) {
    if (ex.getCause() instanceof IllegalStateException ise) {
      log.warn("Entity validation failed: {}", ise.getMessage());
      return ResponseEntity.badRequest()
          .body(createErrorMap("ENTITY_VALIDATION_FAILED", ise.getMessage()));
    }
    log.error("Persistence error: {}", ex.getMessage(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(createErrorMap("PERSISTENCE_ERROR", "A persistence error occurred"));
  }

  // --- External system exceptions ---

  @ExceptionHandler(ExternalSystemRejectionException.class)
  public ResponseEntity<Map<String, Object>> handleExternalSystemRejection(
      ExternalSystemRejectionException ex) {
    log.error("External system rejected request: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(createErrorMap("EXTERNAL_SYSTEM_ERROR", ex.getMessage()));
  }

  @ExceptionHandler(ExternalSystemTimeoutException.class)
  public ResponseEntity<Map<String, Object>> handleExternalSystemTimeout(
      ExternalSystemTimeoutException ex) {
    log.error("External system timed out: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
        .body(createErrorMap("EXTERNAL_SYSTEM_TIMEOUT", ex.getMessage()));
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

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("error", "VALIDATION_FAILED");
    body.put("message", "Input validation failed");
    body.put("fieldErrors", fieldErrors);
    body.put("timestamp", LocalDateTime.now().toString());

    log.warn("Validation failed: {} field error(s)", fieldErrors.size());
    return ResponseEntity.badRequest().body(body);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    log.warn("Malformed request body: {}", ex.getMessage());
    return ResponseEntity.badRequest()
        .body(createErrorMap("MALFORMED_REQUEST", "Request body is missing or malformed"));
  }

  private Map<String, Object> createErrorMap(String error, String message) {
    return Map.of(
        "error", error,
        "message", message,
        "timestamp", LocalDateTime.now().toString());
  }

  private UniqueConstraintViolationException extractUniqueViolationException(String dbMessage) {
    Matcher matcher = UNIQUE_DETAIL_PATTERN.matcher(dbMessage);
    if (matcher.find()) {
      return new UniqueConstraintViolationException(matcher.group(1), matcher.group(2));
    }
    return new UniqueConstraintViolationException();
  }
}
