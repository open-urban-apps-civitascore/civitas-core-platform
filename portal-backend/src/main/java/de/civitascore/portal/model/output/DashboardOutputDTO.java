package de.civitascore.portal.model.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.civitascore.portal.model.embedded.VisualizationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Dashboard visualization read payload")
public final class DashboardOutputDTO extends VisualizationOutputDTO {

  @Schema(description = "UUID of the platform DataSet this dashboard belongs to (1:N)")
  private UUID dataSetId;

  @Schema(description = "Object id in the dashboard engine; null until the engine confirms it")
  private String externalDashboardId;

  @Schema(description = "Engine-sync status; false marks a dashboard the engine no longer serves")
  private boolean active;

  @Override
  @JsonProperty("visualizationType")
  public VisualizationType getVisualizationType() {
    return VisualizationType.DASHBOARD;
  }
}
