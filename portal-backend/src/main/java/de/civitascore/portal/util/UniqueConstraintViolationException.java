package de.civitascore.portal.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a create or update operation would violate a unique constraint. */
@ResponseStatus(HttpStatus.CONFLICT)
public class UniqueConstraintViolationException extends RuntimeException {

  public UniqueConstraintViolationException() {
    super("A record with the same value already exists");
  }

  public UniqueConstraintViolationException(String field, String value) {
    super("A record with %s '%s' already exists".formatted(field, value));
  }

  public UniqueConstraintViolationException(String entityName, String field, String value) {
    super("%s with %s '%s' already exists".formatted(entityName, field, value));
  }

  public UniqueConstraintViolationException(
      String entityName, String field1, String value1, String field2, String value2) {
    super(
        "%s with %s '%s' and %s '%s' already exists"
            .formatted(entityName, field1, value1, field2, value2));
  }

  public UniqueConstraintViolationException(String message) {
    super(message);
  }
}
