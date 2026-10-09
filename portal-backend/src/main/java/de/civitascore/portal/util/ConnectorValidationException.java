package de.civitascore.portal.util;

import jakarta.validation.ConstraintViolation;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Thrown when the connector of a Data source violates the constraints for its release. */
public class ConnectorValidationException extends RuntimeException {

  /** A violated constraint, with the key path of the field in the request body. */
  public record FieldError(String field, String message) {}

  private final transient List<FieldError> fieldErrors;

  public ConnectorValidationException(Set<? extends ConstraintViolation<?>> violations) {
    super("Invalid connector");
    this.fieldErrors =
        violations.stream()
            .map(v -> new FieldError("connector." + v.getPropertyPath(), v.getMessage()))
            .sorted(Comparator.comparing(FieldError::field).thenComparing(FieldError::message))
            .toList();
  }

  public List<FieldError> getFieldErrors() {
    return fieldErrors;
  }
}
