package de.civitascore.portal.util;

import de.civitascore.portal.service.validation.ClosureFinding;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when artifacts participating in a dataset's flows cannot carry a release.
 *
 * <p>Carries every finding across every pipeline rather than the first, so one attempt reports
 * everything that needs repairing.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class PipelineClosureValidationException extends RuntimeException {

  private final List<ClosureFinding> findings;

  public PipelineClosureValidationException(List<ClosureFinding> findings) {
    super(
        "Artifacts participating in this dataset's flows cannot carry a release ("
            + (findings.size() == 1 ? "1 finding" : findings.size() + " findings")
            + "). Each finding names the pipeline concerned, and the artifact where naming it"
            + " discloses nothing; repair or remove what that flow reaches, then try again.");
    this.findings = List.copyOf(findings);
  }

  public List<ClosureFinding> getFindings() {
    return findings;
  }
}
