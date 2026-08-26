package de.civitascore.portal.model.input;

import de.civitascore.portal.model.embedded.ConnectorType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * One data source inside a dataset import bundle. Unlike {@link DataSourceInputDTO} it references
 * its data structure by CORE URN instead of a version UUID: the bundle cannot know server-assigned
 * ids, and the URN resolves both against structures contained in the same bundle and against
 * already installed ones.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSourceImportInputDTO extends BaseDataEntityInputDTO {

  @NotBlank(message = "Name is required") private String name;

  private String description;

  private ConnectorType connectorType;

  @Schema(description = "Connector configuration document, stored in the model registry")
  private Map<String, Object> configuration;

  @NotBlank(message = "dataStructureUrn is required") @Schema(
      description =
          "CORE URN (logical or versioned) of the data structure this source reads — either"
              + " contained in the same bundle or already installed")
  private String dataStructureUrn;
}
