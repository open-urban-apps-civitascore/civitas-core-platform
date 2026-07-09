package de.civitascore.portal.model.datasink;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Data;

/**
 * Configuration shape for a {@code FROST} DataSink. Empty for a passthrough pipeline (the source
 * delivers the SensorThings envelope itself); a mapped pipeline references the Thing-shaped target
 * structure of its final mapping, whose model the dataset publish embeds for the deploy engine.
 */
@Schema(description = "Configuration for a FROST data sink")
@Data
public class FrostConfiguration {

  @Schema(
      description =
          "ID of the DataStructureVersion of the mapping's Thing-shaped target structure;"
              + " required when the pipeline maps into this sink, absent for passthrough")
  private UUID dataStructureVersionId;
}
