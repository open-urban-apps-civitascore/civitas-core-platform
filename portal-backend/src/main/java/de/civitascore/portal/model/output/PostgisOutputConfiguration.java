package de.civitascore.portal.model.output;

import de.civitascore.portal.model.datasink.DataSinkConfiguration;
import de.civitascore.portal.model.output.summary.DataStructureVersionSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Output configuration shape for a {@code POSTGIS} DataSink. */
@Schema(description = "Configuration for a POSTGIS data sink (output)")
@Data
public class PostgisOutputConfiguration implements DataSinkConfiguration {

  @Schema(description = "Target table name in the PostGIS database", example = "traffic_data")
  private String tableName;

  @Schema(description = "The resolved DataStructureVersion that defines the table schema")
  private DataStructureVersionSummaryDTO dataStructureVersion;
}
