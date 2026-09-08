package de.civitascore.portal.service.closure;

import java.util.List;
import java.util.UUID;

/**
 * One reason a participating artifact blocks its dataset from being staged or released.
 *
 * <p>The reason vocabulary is deliberately coarser than the conditions behind it. An artifact that
 * does not resolve, one the platform holds no lifecycle record for, and one the caller may not read
 * all report {@link Reason#NOT_AVAILABLE}: reporting them apart would disclose the existence and
 * name of a model the caller is not entitled to see, since the walk reaches artifacts the caller
 * never named. The distinction is recorded in the log instead.
 *
 * @param pipelineId the pipeline whose flow reaches the artifact
 * @param artifactUrn the versioned CORE URN of the offending artifact
 * @param reason why it blocks
 * @param details diagnostics for {@link Reason#INVALID}; empty for every other reason
 */
public record ClosureFinding(
    UUID pipelineId, String artifactUrn, Reason reason, List<String> details) {

  /** Why a participating artifact blocks publication. */
  public enum Reason {
    /**
     * Does not resolve, has no lifecycle record, or is not readable by the caller — one outward
     * reason for all three, see the type javadoc.
     */
    NOT_AVAILABLE,
    /** Resolves and is readable, but is still a draft. */
    NOT_RELEASED,
    /**
     * Resolves, is readable and is released, but its stored model no longer compiles. Not a
     * complete correctness statement: the registry's schema validation strips CORE-URN {@code
     * $ref}s before compiling, so a reference to a deleted artifact is caught by the closure walk
     * finding that artifact absent, not by this reason.
     */
    INVALID
  }

  public ClosureFinding {
    details = List.copyOf(details);
  }

  public static ClosureFinding notAvailable(UUID pipelineId, String artifactUrn) {
    return new ClosureFinding(pipelineId, artifactUrn, Reason.NOT_AVAILABLE, List.of());
  }

  public static ClosureFinding notReleased(UUID pipelineId, String artifactUrn) {
    return new ClosureFinding(pipelineId, artifactUrn, Reason.NOT_RELEASED, List.of());
  }

  public static ClosureFinding invalid(UUID pipelineId, String artifactUrn, List<String> details) {
    return new ClosureFinding(pipelineId, artifactUrn, Reason.INVALID, details);
  }
}
