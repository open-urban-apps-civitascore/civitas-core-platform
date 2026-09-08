package de.civitascore.portal.service.validation;

import java.util.UUID;

/**
 * One reason a participating artifact blocks its Data Set from being staged or released.
 *
 * <p>The reason vocabulary is deliberately coarser than the conditions behind it. An artifact that
 * does not resolve, one the platform holds no lifecycle record of, and one the caller may not read
 * all report {@link Reason#NOT_AVAILABLE}: telling them apart would disclose the existence and name
 * of a model the caller is not entitled to see, since the walk reaches artifacts the caller never
 * named. The distinction is recorded in the log instead.
 *
 * @param pipelineId the pipeline whose flow reaches the artifact
 * @param artifactUrn the CORE URN of the offending artifact
 * @param reason why it blocks
 */
public record ClosureFinding(UUID pipelineId, String artifactUrn, Reason reason) {

  /** Why a participating artifact blocks publication. */
  public enum Reason {
    /**
     * Does not resolve, has no lifecycle record, or is not readable by the caller — one outward
     * reason for all three, see the type javadoc.
     */
    NOT_AVAILABLE,
    /** Resolves and is readable, but is still a draft. */
    NOT_RELEASED
  }

  public static ClosureFinding notAvailable(UUID pipelineId, String artifactUrn) {
    return new ClosureFinding(pipelineId, artifactUrn, Reason.NOT_AVAILABLE);
  }

  public static ClosureFinding notReleased(UUID pipelineId, String artifactUrn) {
    return new ClosureFinding(pipelineId, artifactUrn, Reason.NOT_RELEASED);
  }
}
