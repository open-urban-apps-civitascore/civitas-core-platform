package de.civitascore.portal.model.output;

import de.civitascore.portal.model.datasink.DataSinkConfigurationOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Data;

/** Output configuration shape for a {@code FROST} DataSink. */
@Schema(description = "Configuration for a FROST data sink (output)")
@Data
public class FrostConfigurationOutput implements DataSinkConfigurationOutput {

  @Schema(
      description =
          "ID of the DataStructureVersion of the mapping's Thing-shaped target structure;"
              + " absent for a passthrough sink")
  private UUID dataStructureVersionId;
}
