package de.civitascore.portal.model.output;

import de.civitascore.portal.model.datasink.DataSinkConfiguration;
import de.civitascore.portal.model.datasink.FrostConfiguration;
import de.civitascore.portal.model.embedded.DataSinkType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO for DataSink entities. */
@Schema(description = "DataSink details")
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSinkOutputDTO extends BaseOutputDTO {

  private UUID dataSetId;
  private UUID pipelineId;
  private DataSinkType dataSinkType;

  @Schema(oneOf = {PostgisOutputConfiguration.class, FrostConfiguration.class})
  private DataSinkConfiguration configuration;
}
