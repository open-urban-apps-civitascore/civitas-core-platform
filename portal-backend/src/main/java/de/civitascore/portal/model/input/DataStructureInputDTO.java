package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating data structure resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataStructureInputDTO extends BaseDataEntityInputDTO {

  @NotBlank(message = "Name is required") private String name;

  private String description;

  @JsonIgnore private DataStructureStatus dataStructureStatus;

  @Schema(description = "Whether this was auto-generated from a data source")
  private Boolean createdFromDataSource;
}
