package de.civitascore.portal.model.output;

import de.civitascore.portal.model.datasink.DataSinkConfigurationOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Output configuration shape for a {@code POSTGIS} DataSink. */
@Schema(description = "Configuration for a POSTGIS data sink (output)")
@Data
public class PostgisConfigurationOutput implements DataSinkConfigurationOutput {

  @Schema(description = "Target table name in the PostGIS database", example = "traffic_data")
  private String tableName;

  @Schema(
      description =
          "Versioned CORE URN of the Element (a DataStructureVersion's model) that defines the"
              + " table schema")
  private String element;
}
