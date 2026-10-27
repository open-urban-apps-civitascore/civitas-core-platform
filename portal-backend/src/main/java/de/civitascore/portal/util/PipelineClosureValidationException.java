package de.civitascore.portal.util;

import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thrown when the artifacts a Data Set's flows reach cannot carry a release.
 *
 * <p>Names the offending pipelines and not the artifacts behind them: an artifact the caller may
 * not read would otherwise be disclosed by its name, and telling an absent artifact from a withheld
 * one would disclose it just as well. The message states every condition instead, and the log
 * records which one applied.
 */
public class PipelineClosureValidationException extends ProblemException {

  private final List<UUID> offendingPipelineIds;

  public PipelineClosureValidationException(List<UUID> offendingPipelineIds) {
    this(
        "One or more Pipelines reach an artifact that cannot carry a release. Each artifact a"
            + " Pipeline reaches must exist, be readable, and be released: "
            + offendingPipelineIds,
        offendingPipelineIds);
  }

  private PipelineClosureValidationException(String message, List<UUID> offendingPipelineIds) {
    super(ProblemType.PIPELINE_CLOSURE_INVALID, message);
    this.offendingPipelineIds = List.copyOf(offendingPipelineIds);
  }

  public List<UUID> getOffendingPipelineIds() {
    return offendingPipelineIds;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("offendingPipelineIds", offendingPipelineIds);
  }
}
