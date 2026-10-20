package de.civitascore.portal.model.output.summary;

import de.civitascore.portal.model.embedded.PipelineRuntimeSource;
import de.civitascore.portal.model.embedded.PipelineRuntimeState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** Read-only runtime status of a pipeline, exposed alongside {@link PipelineSummaryDTO}. */
@Data
public class PipelineRuntimeStatusDTO {
  @Schema(
      description =
          "OK: the Pipeline runs. ERROR: the deployment failed, or the Pipeline failed while"
              + " running and tries again at its next run. STOPPED: the platform stopped the"
              + " Pipeline after a fatal error, and message holds the cause. To run a stopped"
              + " Pipeline again, unrelease and release the Dataset.")
  private PipelineRuntimeState state;

  private String message;
  private String sanitizedStacktrace;
  private Instant occurredAt;
  private PipelineRuntimeSource source;
  private UUID lastEventId;
}
