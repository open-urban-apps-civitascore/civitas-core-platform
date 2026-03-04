package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataStructureInputDTO extends BaseDataEntityInputDTO {
  @NotBlank(message = "Name is required") private String name;

  private String description;

  @JsonIgnore private DataStructureStatus dataStructureStatus;

  private Boolean createdFromDataSource;
  private List<UUID> dataStructureVersionIds;
}
