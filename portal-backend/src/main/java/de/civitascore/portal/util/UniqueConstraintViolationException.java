package de.civitascore.portal.util;

import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;

/**
 * Thrown when a create or update operation would violate a unique constraint. The message is the
 * detail of the answer: it names the field and the value the client sent, so that the client can
 * show which field collided (TR-03187 W-18/W-19).
 */
public class UniqueConstraintViolationException extends ProblemException {

  public UniqueConstraintViolationException() {
    this("A record with the same value already exists");
  }

  public UniqueConstraintViolationException(String field, String value) {
    this("A record with %s '%s' already exists".formatted(field, value));
  }

  public UniqueConstraintViolationException(String entityName, String field, String value) {
    this("%s with %s '%s' already exists".formatted(entityName, field, value));
  }

  public UniqueConstraintViolationException(
      String entityName, String field1, String value1, String field2, String value2) {
    this(
        "%s with %s '%s' and %s '%s' already exists"
            .formatted(entityName, field1, value1, field2, value2));
  }

  public UniqueConstraintViolationException(String message) {
    super(ProblemType.UNIQUE_CONSTRAINT_VIOLATION, message);
  }
}
