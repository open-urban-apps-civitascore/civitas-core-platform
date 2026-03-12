package de.civitascore.portal.model.input;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataSourceMetaInputDTO extends BaseDataEntityInputDTO {
  @NotBlank(message = "Name is required") private String name;

  private String description;

  private Map<String, Object> configuration;
}
