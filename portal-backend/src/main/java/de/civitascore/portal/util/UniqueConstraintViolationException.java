package de.civitascore.portal.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class UniqueConstraintViolationException extends RuntimeException {

  public UniqueConstraintViolationException(String entityName, String field, String value) {
    super(String.format("%s with %s '%s' already exists", entityName, field, value));
  }

  public UniqueConstraintViolationException(
      String entityName, String field1, String value1, String field2, String value2) {
    super(
        String.format(
            "%s with %s '%s' and %s '%s' already exists",
            entityName, field1, value1, field2, value2));
  }

  public UniqueConstraintViolationException(String message) {
    super(message);
  }
}
