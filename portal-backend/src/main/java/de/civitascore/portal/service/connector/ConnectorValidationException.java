package de.civitascore.portal.service.connector;

import de.civitascore.portal.problem.FieldError;
import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;
import java.util.List;
import java.util.Map;

/** Thrown when the connector of a Data source violates the rules of a validation stage. */
public class ConnectorValidationException extends ProblemException {

  private final transient List<ConnectorViolation> violations;

  public ConnectorValidationException(List<ConnectorViolation> violations) {
    super(ProblemType.VALIDATION_FAILED, "Invalid connector", null);
    this.violations = List.copyOf(violations);
  }

  public List<ConnectorViolation> getViolations() {
    return violations;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of(
        "fieldErrors",
        violations.stream()
            .map(violation -> new FieldError(violation.path(), violation.message()))
            .toList());
  }
}
