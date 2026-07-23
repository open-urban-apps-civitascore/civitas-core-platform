package de.civitascore.portal.model.output.summary;

import de.civitascore.portal.model.embedded.PipelineRuntimeSource;
import de.civitascore.portal.model.embedded.PipelineRuntimeState;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** Read-only runtime status of a pipeline, exposed alongside {@link PipelineSummaryDTO}. */
@Data
public class PipelineRuntimeStatusDTO {
  private PipelineRuntimeState state;
  private String message;
  private String sanitizedStacktrace;
  private Instant occurredAt;
  private PipelineRuntimeSource source;
  private UUID lastEventId;
}
