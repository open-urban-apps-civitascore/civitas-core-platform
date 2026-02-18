package de.civitascore.portal.model.input;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PipelineInputDTO extends BaseInputDTO {

  @NotBlank(message = "Name is required") private String name;

  private String description;

  @NotNull(message = "DataSet ID is required") private UUID dataSetId;

  private Map<String, Object> styles;

  private Set<UUID> dataSourceIds;

  private String[] apis;

  private Long[] persistences;

  private Map<String, Object> model;
}
