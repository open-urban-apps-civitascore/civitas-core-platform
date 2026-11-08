package de.civitascore.portal.model.output;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.civitascore.portal.model.embedded.VisualizationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Read-side mirror of {@link de.civitascore.portal.model.input.VisualizationInputDTO}. */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "visualizationType",
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    visible = false)
@JsonSubTypes({@JsonSubTypes.Type(value = DashboardOutputDTO.class, name = "DASHBOARD")})
@Schema(
    description = "Visualization read payload. Structure depends on visualizationType.",
    oneOf = {DashboardOutputDTO.class},
    discriminatorProperty = "visualizationType")
public abstract sealed class VisualizationOutputDTO extends BaseOutputDTO
    permits DashboardOutputDTO {

  @Schema(description = "Human-readable name of the visualization")
  private String name;

  @Schema(description = "Optional human-readable description")
  private String description;

  public abstract VisualizationType getVisualizationType();
}
