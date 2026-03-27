package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating pipeline resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PipelineInputDTO extends BaseInputDTO {

  @NotBlank(message = "Name is required") private String name;

  private String description;

  private Map<String, Object> styles;
  private Map<String, Object> model;

  private Set<UUID> dataSourceIds;
  private String[] apis;
  private Long[] persistences;

  @JsonIgnore private UUID dataSetId;
}
