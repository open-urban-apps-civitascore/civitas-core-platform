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

  /** Read-only projection of {@link #element}; the input side stays URN-only. */
  @Schema(
      description =
          "The DataStructureVersion pinned by 'element', resolved via its model URN; absent when"
              + " no stored version carries that URN",
      accessMode = Schema.AccessMode.READ_ONLY)
  private DataStructureVersionSummaryDTO dataStructureVersion;
}
