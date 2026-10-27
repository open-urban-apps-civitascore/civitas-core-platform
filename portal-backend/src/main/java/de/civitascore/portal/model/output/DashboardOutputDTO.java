package de.civitascore.portal.model.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.civitascore.portal.model.embedded.DashboardFilterExpansion;
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

  @Schema(description = "Whether the dashboard is published in the dashboard engine")
  private boolean released;

  @Schema(description = "Superset standalone display mode from 0 to 3")
  private int standaloneMode;

  @Schema(description = "Whether Superset shows the dashboard filter bar")
  private boolean showFilters;

  @Schema(description = "Initial filter bar state: DEFAULT, COLLAPSED or EXPANDED")
  private DashboardFilterExpansion filterExpansion;

  @Override
  @JsonProperty("visualizationType")
  public VisualizationType getVisualizationType() {
    return VisualizationType.DASHBOARD;
  }
}
