package de.civitascore.portal.service.connector;

import java.util.List;

/** Thrown when the connector of a Data source violates the rules of a validation stage. */
public class ConnectorValidationException extends RuntimeException {

  private final transient List<ConnectorViolation> violations;

  public ConnectorValidationException(List<ConnectorViolation> violations) {
    super("Invalid connector");
    this.violations = List.copyOf(violations);
  }

  public List<ConnectorViolation> getViolations() {
    return violations;
  }
}
