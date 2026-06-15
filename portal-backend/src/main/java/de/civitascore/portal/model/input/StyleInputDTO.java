package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating Style resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class StyleInputDTO extends BaseInputDTO {

  @NotBlank private String name;

  @NotBlank private String sldContent;

  @JsonIgnore private UUID dataSetId;
}
