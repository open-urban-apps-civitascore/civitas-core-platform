package de.civitascore.portal.model.input;

import de.civitascore.portal.model.datasink.FrostConfiguration;
import de.civitascore.portal.model.datasink.PostgisConfiguration;
import de.civitascore.portal.model.embedded.DataSinkType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating DataSink resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSinkInputDTO extends BaseInputDTO {

  private UUID id;

  @NotNull private DataSinkType dataSinkType;

  private UUID pipelineId;

  @NotNull @Schema(oneOf = {PostgisConfiguration.class, FrostConfiguration.class})
  private Map<String, Object> configuration;
}
