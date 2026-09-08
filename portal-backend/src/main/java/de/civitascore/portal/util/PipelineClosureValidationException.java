package de.civitascore.portal.util;

import de.civitascore.portal.service.validation.ClosureFinding;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when artifacts participating in a dataset's flows cannot carry a release.
 *
 * <p>Carries every finding across every pipeline rather than the first, so one release attempt
 * reports everything that needs repairing instead of requiring an attempt per defect.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class PipelineClosureValidationException extends RuntimeException {

  private final List<ClosureFinding> findings;

  public PipelineClosureValidationException(List<ClosureFinding> findings) {
    // Name the way out: each finding states an artifact and a reason, and the flow can only be
    // released once every one of them is resolved or removed from the flow.
    super(
        "Artifacts participating in this dataset's flows cannot carry a release ("
            + findings.size()
            + " finding(s)). Release, repair or replace each artifact listed in the findings, or"
            + " remove it from the flow.");
    this.findings = List.copyOf(findings);
  }

  public List<ClosureFinding> getFindings() {
    return findings;
  }
}
