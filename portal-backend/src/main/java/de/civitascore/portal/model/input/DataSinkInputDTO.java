package de.civitascore.portal.model.input;

import de.civitascore.portal.model.datasink.FrostConfiguration;
import de.civitascore.portal.model.datasink.PostgisConfiguration;
import de.civitascore.portal.model.embedded.DataSinkType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating DataSink resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSinkInputDTO extends DataSetOwnedInputDTO {

  @NotNull private DataSinkType dataSinkType;

  @NotNull @Schema(oneOf = {PostgisConfiguration.class, FrostConfiguration.class})
  private Map<String, Object> configuration;

  @Schema(
      description =
          "Acknowledges that this update rebuilds the sink's table and discards all stored data."
              + " Required (true) when tableName or the referenced element changes on a"
              + " provisioned sink; ignored otherwise.")
  private boolean confirmDataLoss;
}
