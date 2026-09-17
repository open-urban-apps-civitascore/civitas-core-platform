package de.civitascore.portal.util;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the artifacts a Data Set's flows reach cannot carry a release.
 *
 * <p>Names the offending pipelines and not the artifacts behind them: an artifact the caller may
 * not read would otherwise be disclosed by its name, and telling an absent artifact from a withheld
 * one would disclose it just as well. The message states every condition instead, and the log
 * records which one applied.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class PipelineClosureValidationException extends RuntimeException {

  private final List<UUID> offendingPipelineIds;

  public PipelineClosureValidationException(List<UUID> offendingPipelineIds) {
    super(
        "One or more Pipelines reach an artifact that cannot carry a release. Each artifact a"
            + " Pipeline reaches must exist, be readable, and be released: "
            + offendingPipelineIds);
    this.offendingPipelineIds = List.copyOf(offendingPipelineIds);
  }

  public List<UUID> getOffendingPipelineIds() {
    return offendingPipelineIds;
  }
}
