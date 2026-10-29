package de.civitascore.portal.model.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSinkType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** What a removal of Pipelines, Pipeline links or Data sinks takes with it. */
@Schema(
    description =
        "What a removal takes with it: the Data sinks that become inactive, and the Layers and"
            + " APIs that are deleted. A confirming request sends confirmationIds back as"
            + " confirmedRemovalIds.")
public record DataSinkRemovalImpactDTO(
    @Schema(description = "Data sinks that become inactive.") List<DataSinkImpact> dataSinks,
    @Schema(description = "Layers that are deleted.") List<LayerImpact> layers,
    @Schema(description = "APIs that are deleted.") List<ApiImpact> apis,
    @Schema(description = "The ids a request must name in confirmedRemovalIds.")
        List<UUID> confirmationIds) {

  @JsonProperty("requiresConfirmation")
  @Schema(
      description =
          "Whether the removal deletes data, a Layer or an API and therefore needs"
              + " confirmation, that is whether confirmationIds is not empty.")
  public boolean requiresConfirmation() {
    return !confirmationIds.isEmpty();
  }

  /** A Data sink that becomes inactive. */
  public record DataSinkImpact(
      UUID id,
      DataSinkType dataSinkType,
      @Schema(
              description =
                  "Whether its storage exists. Its stored data is deleted at the next release.")
          boolean provisioned) {}

  /** A Layer that is deleted. */
  public record LayerImpact(UUID id, String layerName, String title) {}

  /** An API that is deleted. */
  public record ApiImpact(UUID id, String slug, String name, ApiStandard standard) {}
}
