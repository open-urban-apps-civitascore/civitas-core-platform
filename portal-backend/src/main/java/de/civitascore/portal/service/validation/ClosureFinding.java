package de.civitascore.portal.service.validation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * One reason a participating artifact blocks its Data Set from being staged or released.
 *
 * <p>The reason vocabulary is deliberately coarser than the conditions behind it. An artifact that
 * does not resolve and one the caller may not read both report {@link Reason#NOT_AVAILABLE}:
 * telling them apart would disclose the existence and name of a model the caller is not entitled to
 * see, since the walk reaches artifacts the caller never named. The distinction is recorded in the
 * log instead. An artifact the platform holds no lifecycle record of is not reported at all — it is
 * held to resolvability alone.
 *
 * <p>For the same reason a {@code NOT_AVAILABLE} finding carries <b>no</b> {@code artifactUrn}. A
 * CORE URN spells the model's display name in one of its segments, so returning it would name a
 * model of another department and would also let the caller tell an absent artifact from one merely
 * withheld — by recognising the URNs their own documents author. Withholding it is what makes the
 * single reason above true on the wire. The full URN goes to the log.
 *
 * <p>A {@code NOT_RELEASED} finding does carry the URN: reaching that verdict required the caller
 * to be allowed to read the artifact, so naming it discloses nothing and is what makes the finding
 * actionable.
 *
 * @param pipelineId the pipeline whose flow reaches the artifact
 * @param artifactUrn the CORE URN of the offending artifact; {@code null}, and omitted from the
 *     response, for {@link Reason#NOT_AVAILABLE}
 * @param reason why it blocks
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClosureFinding(UUID pipelineId, String artifactUrn, Reason reason) {

  /** Why a participating artifact blocks publication. */
  public enum Reason {
    /**
     * Does not resolve, or is not readable by the caller — one outward reason for both, see the
     * type javadoc.
     */
    NOT_AVAILABLE,
    /** Resolves and is readable, but is still a draft. */
    NOT_RELEASED
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
}
