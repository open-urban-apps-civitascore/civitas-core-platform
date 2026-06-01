package de.civitascore.portal.model.datasink;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Data;

/** Input configuration shape for a {@code POSTGIS} DataSink. */
@Schema(description = "Configuration for a POSTGIS data sink")
@Data
public class PostgisConfiguration {

  @Schema(description = "Target table name in the PostGIS database", example = "traffic_data")
  private String tableName;

  @Schema(description = "ID of the DataStructureVersion that defines the table schema")
  private UUID dataStructureVersionId;
}
