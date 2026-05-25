package de.civitascore.portal.model.output;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO for Pipeline entity. */
@Schema(description = "Pipeline details")
@Data
@EqualsAndHashCode(callSuper = true)
public class PipelineOutputDTO extends BaseOutputDTO {

  private String name;
  private String description;
  private Map<String, Object> styles;
  private List<DataSinkOutputDTO> dataSinks = new ArrayList<>();
  private Map<String, Object> model;
}
