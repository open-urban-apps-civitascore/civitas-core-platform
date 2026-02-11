package de.civitascore.portal.model.output;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO for Pipeline entity. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PipelineOutputDTO extends BaseOutputDTO {

  private String name;
  private String description;
  private String styles;
  private Long[] dataSources;
  private String[] apis;
  private Long[] persistences;
  private String model;
}
