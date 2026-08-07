package de.civitascore.portal.model.output.summary;

import de.civitascore.portal.model.embedded.ConnectorType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Lightweight summary DTO for data source entities, used in list endpoints and nested references.
 *
 * <p>Carries what picking a data source needs — id, name, description and connector type. The
 * connector <em>configuration</em> stays out deliberately: this is what {@code GET
 * /datasets/{id}/usable-datasources} returns to a caller authorized on the dataset rather than on
 * the data source. The bare type is included because a pipeline's shape depends on it — an MQTT
 * source is push-driven and emits a different payload form than a polled SQL source — so an editor
 * that cannot see it cannot validate what it builds.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(
    description =
        "Lightweight data source reference — id, name, description and connector type, no connector"
            + " configuration.")
public class DataSourceSummaryDTO extends BaseSummaryNamedDTO {

  @Schema(example = "Real-time traffic sensor data via MQTT broker")
  private String description;

  @Schema(description = "Connector kind the source uses; drives the payload form of a pipeline")
  private ConnectorType connectorType;
}
