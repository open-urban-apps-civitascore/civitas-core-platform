package de.civitascore.portal.model.output;

import de.civitascore.portal.model.datasink.DataSinkConfigurationOutput;
import de.civitascore.portal.model.output.summary.DataStructureVersionSummaryDTO;
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

  @Schema(
      description =
          "Summary of the DataStructureVersion the element URN resolves to; absent when the URN"
              + " does not resolve to a stored version. The OWS named-API editor reads the version"
              + " by database id from here — the document itself only carries the URN.")
  private DataStructureVersionSummaryDTO dataStructureVersion;
}
