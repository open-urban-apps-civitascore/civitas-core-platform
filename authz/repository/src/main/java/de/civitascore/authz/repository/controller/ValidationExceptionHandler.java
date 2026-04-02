package de.civitascore.authz.repository.controller;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handles Bean Validation constraint violations for path variable validation.
 *
 * <p>Without this handler, {@link ConstraintViolationException} from {@code @Validated} controllers
 * would result in a 500 Internal Server Error. This maps it to a 400 Bad Request instead.
 */
@Slf4j
@RestControllerAdvice
public class ValidationExceptionHandler {

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Void> handleConstraintViolation(ConstraintViolationException ex) {
    log.debug("Constraint violation: {}", ex.getMessage());
    return ResponseEntity.badRequest().build();
  }
}
