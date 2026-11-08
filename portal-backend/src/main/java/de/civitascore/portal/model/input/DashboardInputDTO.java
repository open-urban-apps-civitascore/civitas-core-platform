package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.civitascore.portal.model.embedded.DashboardFilterExpansion;
import de.civitascore.portal.model.embedded.VisualizationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Dashboard visualization write payload")
public final class DashboardInputDTO extends VisualizationInputDTO {

  /** Injected by the controller from the {dataSetId} path variable; never read from the body. */
  @JsonIgnore private UUID dataSetId;

  @Schema(description = "Whether the dashboard is published in the dashboard engine")
  private Boolean released;

  @Min(0) @Max(3) @Schema(description = "Superset standalone display mode from 0 to 3")
  private Integer standaloneMode;

  @Schema(description = "Whether Superset shows the dashboard filter bar")
  private Boolean showFilters;

  @Schema(description = "Initial filter bar state: DEFAULT, COLLAPSED or EXPANDED")
  private DashboardFilterExpansion filterExpansion;

  @Override
  @JsonProperty("visualizationType")
  public VisualizationType getVisualizationType() {
    return VisualizationType.DASHBOARD;
  }
}
