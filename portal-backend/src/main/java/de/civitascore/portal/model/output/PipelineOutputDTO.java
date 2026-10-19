package de.civitascore.portal.model.output;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO for Pipeline entity. */
@Schema(description = "Pipeline details")
@Data
@EqualsAndHashCode(callSuper = true)
public class PipelineOutputDTO extends BaseOutputDTO {

  private UUID dataSetId;
  private String name;
  private String description;
  private Map<String, Object> styles;
  private List<UUID> dataSinkIds = new ArrayList<>();
  private List<UUID> dataSourceIds = new ArrayList<>();
  private Map<String, Object> model;

  /** Versioned CORE URN of this pipeline's model artifact in Model Forge. */
  private String modelUrn;
}
