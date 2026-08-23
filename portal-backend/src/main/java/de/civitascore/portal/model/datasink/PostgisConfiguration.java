package de.civitascore.portal.model.datasink;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Input configuration shape for a {@code POSTGIS} DataSink. */
@Schema(description = "Configuration for a POSTGIS data sink")
@Data
public class PostgisConfiguration {

  @Schema(
      description =
          "Target table name in the PostGIS database. Must be a plain unquoted SQL identifier and"
              + " unique across the dataset's POSTGIS sinks, which all share one schema.",
      pattern = "^[A-Za-z_][A-Za-z0-9_]*$",
      maxLength = 63,
      example = "traffic_data")
  private String tableName;

  @Schema(
      description =
          "Versioned CORE URN of the Element (a DataStructureVersion's model) that defines the"
              + " output row format. Model Forge tracks this as a datasink-element dependency edge.")
  private String element;
}
