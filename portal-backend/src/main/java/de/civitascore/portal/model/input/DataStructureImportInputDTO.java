package de.civitascore.portal.model.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Input DTO for importing a complete data structure in a single call: the data structure shell, its
 * first version and the model content stored in Model Forge.
 *
 * <p>Combines the fields of {@link DataStructureInputDTO} and {@link DataStructureVersionInputDTO}
 * that make sense for an import: lifecycle status is not a client input (everything starts in
 * DRAFT), the version string is assigned by Model Forge, and the version source is always OWN.
 * Extends {@link BaseDataEntityInputDTO} so the caller can scope the imported structure via
 * assignments, exactly as on the plain create endpoint.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataStructureImportInputDTO extends BaseDataEntityInputDTO {

  @NotBlank(message = "Name is required") private String name;

  private String description;

  @Schema(description = "Description for the created first version")
  private String versionDescription;

  private String modelName;

  @Schema(
      description =
          "Data model definition as a JSON Schema document (required — an import without content"
              + " would only create an empty shell)")
  @NotEmpty(message = "Model is required") private Map<String, Object> model;

  @Schema(description = "UI layout for the model, stored alongside it as x-ui-styles")
  private Map<String, Object> styles;

  @Size(max = 1024, message = "catalogEntryId must not exceed 1024 characters") @Schema(
      description =
          "Catalogue identity of the entry this import comes from, in whatever scheme the catalogue"
              + " uses. Recorded verbatim in the install provenance, never interpreted — an import"
              + " without catalogue identity simply records none.")
  private String catalogEntryId;

  @Size(max = 255, message = "catalogEntryVersion must not exceed 255 characters") @Schema(description = "Version of the catalogue entry, recorded alongside catalogEntryId")
  private String catalogEntryVersion;
}
