package de.civitascore.portal.util;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a pipeline's participating closure holds more artifacts than the configured bound, so
 * the flow is refused without examining them.
 *
 * <p>Separate from {@link PipelineClosureValidationException} because it names no offending
 * artifact: the closure was never examined, and reporting a bound as if it were a defect of some
 * artifact in it would be untrue. Refusing beats examining a truncated closure, which would report
 * on the artifacts before the cut and stay silent about the rest.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class PipelineClosureTooLargeException extends RuntimeException {

  /**
   * @param pipelineId the pipeline whose closure exceeds the bound
   * @param artifactCount how many artifacts its closure holds
   * @param maxArtifacts the configured bound
   */
  public record OversizedClosure(UUID pipelineId, int artifactCount, int maxArtifacts) {}

  private final List<OversizedClosure> oversizedClosures;

  public PipelineClosureTooLargeException(List<OversizedClosure> oversizedClosures) {
    // Name the way out: both resolutions are legitimate, and which one applies is an operator
    // judgement about whether the model is genuinely this large.
    super(
        "The artifacts participating in "
            + oversizedClosures.size()
            + " pipeline flow(s) exceed the configured bound, so they were not examined. Reduce"
            + " the flow's dependencies, or raise dataset.closure-validation.max-artifacts.");
    this.oversizedClosures = List.copyOf(oversizedClosures);
  }

  public List<OversizedClosure> getOversizedClosures() {
    return oversizedClosures;
  }
}
