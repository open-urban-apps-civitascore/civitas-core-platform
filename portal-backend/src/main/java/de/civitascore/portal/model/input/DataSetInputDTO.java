package de.civitascore.portal.model.input;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataSetInputDTO extends BaseInputDTO {

  @NotBlank(message = "Name is required") @Size(min = 3, max = 255, message = "Name must be between 3 and 255 characters") private String name;

  private String description;

  private List<UUID> pipelineIds;

  private Boolean openDataAccess;
}
