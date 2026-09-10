package de.civitascore.portal.model.output;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.civitascore.portal.model.datasink.DataSinkConfigurationOutput;
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
  private boolean inUse;

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = FrostConfigurationOutput.class, name = "FROST"),
    @JsonSubTypes.Type(value = PostgisConfigurationOutput.class, name = "POSTGIS")
  })
  @Schema(oneOf = {PostgisConfigurationOutput.class, FrostConfigurationOutput.class})
  private DataSinkConfigurationOutput configuration;

  @Schema(
      description = "Versioned CORE URN of this DataSink's configuration artifact in Model Forge")
  private String configurationUrn;
}
