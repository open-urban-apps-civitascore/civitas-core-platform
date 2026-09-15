package de.civitascore.portal.service.validation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * One reason a participating artifact blocks its Data Set from being staged or released.
 *
 * <p>An artifact that does not resolve and one the caller may not read share {@link
 * Reason#NOT_AVAILABLE} and name no artifact: a CORE URN spells the model's display name, so
 * returning it would disclose a model of another scope and let the two conditions be told apart.
 * The full URN goes to the log. {@link Reason#NOT_RELEASED} does name the artifact — that verdict
 * required the caller to be allowed to read it.
 *
 * @param pipelineId the pipeline whose flow reaches the artifact
 * @param artifactUrn the offending artifact, absent for every reason that names none
 * @param reason why it blocks
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClosureFinding(UUID pipelineId, String artifactUrn, Reason reason) {

  /** Why a participating artifact blocks publication. */
  public enum Reason {
    /** Does not resolve, or is not readable by the caller. */
    NOT_AVAILABLE,
    /** Resolves and is readable, but is still a draft. */
    NOT_RELEASED,
    /** The flow reaches further than the walk follows, so part of it was never examined. */
    NOT_VERIFIED
  }

  /**
   * A finding that names no artifact. Every condition behind it is one the caller may not be told
   * apart, so the offending URN stays in the log; the pipeline is what the caller can act on.
   */
  public static ClosureFinding notAvailable(UUID pipelineId) {
    return new ClosureFinding(pipelineId, null, Reason.NOT_AVAILABLE);
  }

  public static ClosureFinding notReleased(UUID pipelineId, String artifactUrn) {
    return new ClosureFinding(pipelineId, artifactUrn, Reason.NOT_RELEASED);
  }

  /** A finding about the walk rather than an artifact, so it names none. */
  public static ClosureFinding notVerified(UUID pipelineId) {
    return new ClosureFinding(pipelineId, null, Reason.NOT_VERIFIED);
  }
}
