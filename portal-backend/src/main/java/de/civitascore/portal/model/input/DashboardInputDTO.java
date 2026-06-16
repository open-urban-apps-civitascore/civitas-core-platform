package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.civitascore.portal.model.embedded.VisualizationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Dashboard visualization write payload")
public final class DashboardInputDTO extends VisualizationInputDTO {

  /** Injected by the controller from the {dataSetId} path variable; never read from the body. */
  @JsonIgnore private UUID dataSetId;

  @Override
  @JsonProperty("visualizationType")
  public VisualizationType getVisualizationType() {
    return VisualizationType.DASHBOARD;
  }
}
