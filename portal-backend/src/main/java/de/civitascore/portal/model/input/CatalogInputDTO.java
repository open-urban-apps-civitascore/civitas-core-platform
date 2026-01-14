package de.civitascore.portal.model.input;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CatalogInputDTO extends BaseInputDTO {
  @NotBlank(message = "Name is required") private String name;

  private String description;
  private List<UUID> childCatalogIds;
  private List<UUID> dataSetIds;
}
