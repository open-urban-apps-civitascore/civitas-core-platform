package de.civitascore.portal.model.output;

import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO for Pipeline entity. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PipelineOutputDTO extends BaseOutputDTO {

  private String name;
  private String description;
  private Map<String, Object> styles;
  private Long[] dataSources;
  private String[] apis;
  private Long[] persistences;
  private Map<String, Object> model;
}
