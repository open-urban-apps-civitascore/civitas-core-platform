package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.civitascore.configadapter.model.dataset.SafeNames;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating Style resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class StyleInputDTO extends BaseInputDTO {

  @NotBlank @Pattern(
      regexp = SafeNames.PATTERN,
      message = "Style name must contain only letters, digits, underscores or hyphens")
  private String name;

  @NotBlank private String sldContent;

  @JsonIgnore private UUID dataSetId;
}
