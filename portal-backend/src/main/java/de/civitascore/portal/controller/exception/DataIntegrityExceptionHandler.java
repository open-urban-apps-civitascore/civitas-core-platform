package de.civitascore.portal.controller.exception;

import de.civitascore.portal.util.UniqueConstraintViolationException;
import jakarta.persistence.PersistenceException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class DataIntegrityExceptionHandler {

  private static final Pattern UNIQUE_DETAIL_PATTERN =
      Pattern.compile("Key \\((.+?)\\)=\\((.+?)\\) already exists");

  private static final String UNIQUE_VIOLATION = "23505";
  private static final String FOREIGN_KEY_VIOLATION = "23503";
  private static final String NOT_NULL_VIOLATION = "23502";

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
      DataIntegrityViolationException ex) {

    if (ex.getCause() instanceof ConstraintViolationException cve) {
      String sqlState = cve.getSQLState();

      if (UNIQUE_VIOLATION.equals(sqlState)) {
        String dbMessage = ex.getMostSpecificCause().getMessage();
        UniqueConstraintViolationException mapped = extractUniqueViolationException(dbMessage);
        log.warn("Unique constraint violation: {}", mapped.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(createErrorMap("UNIQUE_CONSTRAINT_VIOLATION", mapped.getMessage()));
      }

      if (FOREIGN_KEY_VIOLATION.equals(sqlState)) {
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

      if (NOT_NULL_VIOLATION.equals(sqlState)) {
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
