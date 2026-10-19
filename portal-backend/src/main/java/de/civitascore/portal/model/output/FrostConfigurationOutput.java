package de.civitascore.portal.model.output;

import de.civitascore.portal.model.datasink.DataSinkConfigurationOutput;
import de.civitascore.portal.model.datasink.FrostSinkPort;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Output configuration shape for a {@code FROST} DataSink. */
@Schema(description = "Configuration for a FROST data sink (output)")
@Data
public class FrostConfigurationOutput implements DataSinkConfigurationOutput {

  @Schema(description = "The write logic of the sink; absent for a sink that is not configured yet")
  private FrostSinkPort port;

  @Schema(
      description =
          "Versioned CORE URN of the Element (a DataStructureVersion's model) of the mapping's"
              + " Thing-shaped target structure; absent for a passthrough sink")
  private String element;
}
