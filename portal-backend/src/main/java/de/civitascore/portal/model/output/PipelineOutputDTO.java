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
  private List<DataSinkOutputDTO> dataSinks = new ArrayList<>();
  private Map<String, Object> model;
}
