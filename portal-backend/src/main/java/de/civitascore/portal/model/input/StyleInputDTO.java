package de.civitascore.portal.model.input;

import de.civitascore.configadapter.model.dataset.SafeNames;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating Style resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class StyleInputDTO extends DataSetOwnedInputDTO {

  @NotBlank @Pattern(
      regexp = SafeNames.PATTERN,
      message = "Style name must contain only letters, digits, underscores or hyphens")
  private String name;

  @NotBlank private String sldContent;
}
