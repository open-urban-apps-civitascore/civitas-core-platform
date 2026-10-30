package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.civitascore.portal.model.embedded.VisualizationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Discriminated union of visualization write payloads. The discriminator is derived from the type
 * rather than stored as a free-standing field: each member returns its own {@link
 * VisualizationType} and Jackson reads it back off the {@code visualizationType} property, so the
 * value can never drift out of sync with the concrete subtype. Today {@code DashboardInputDTO} is
 * the only member; map / report are foreseen additive members.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "visualizationType",
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    visible = false)
@JsonSubTypes({@JsonSubTypes.Type(value = DashboardInputDTO.class, name = "DASHBOARD")})
@Schema(
    description = "Visualization write payload. Structure depends on visualizationType.",
    oneOf = {DashboardInputDTO.class},
    discriminatorProperty = "visualizationType")
public abstract sealed class VisualizationInputDTO extends BaseInputDTO permits DashboardInputDTO {

  @NotBlank @Size(max = 255) @Schema(description = "Human-readable name of the visualization")
  private String name;

  @Schema(description = "Optional human-readable description")
  private String description;

  public abstract VisualizationType getVisualizationType();
}
