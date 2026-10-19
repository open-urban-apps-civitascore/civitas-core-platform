package de.civitascore.portal.model.datasink;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Configuration shape for a {@code FROST} DataSink. The port declares what the sink writes; the
 * element references the target structure of the pipeline's final mapping, whose model the dataset
 * publish embeds for the deploy engine.
 */
@Schema(description = "Configuration for a FROST data sink")
@Data
public class FrostConfiguration {

  @Schema(
      description =
          "The write logic of the sink. There is no default: a sink without a port is not"
              + " configured, and the dataset does not publish.",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private FrostSinkPort port;

  @Schema(
      description =
          "Versioned CORE URN of the Element (a DataStructureVersion's model) of the mapping's"
              + " Thing-shaped target structure; required when the pipeline maps into this sink,"
              + " absent for passthrough")
  private String element;
}
