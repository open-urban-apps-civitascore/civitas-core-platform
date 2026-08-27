package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.modelregistry.VersionBump;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Input DTO for creating and updating data structure version resources.
 *
 * <p>The version string is no longer a client input: Model Forge is the sole version authority and
 * assigns it when the model is stored (see {@code MODEL-FORGE-INTEGRATION-PLAN.md}).
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataStructureVersionInputDTO extends BaseInputDTO {

  @JsonIgnore private DataStructureVersionStatus dataStructureVersionStatus;

  @Schema(description = "How this version was created (required)")
  @NotNull(message = "DataStructureVersionSource is required") private DataStructureVersionSource dataStructureVersionSource;

  @Schema(
      description =
          "Requested change class for a follow-up version (MAJOR/MINOR/PATCH); Model Forge assigns"
              + " the concrete version. Ignored for the first version. Defaults to MINOR.")
  private VersionBump versionBump = VersionBump.MINOR;

  private String description;

  private String modelName;

  @Schema(description = "Data model definition as a JSON Schema document")
  private Map<String, Object> model;

  private Map<String, Object> styles;

  @JsonIgnore
  // The dataStructureId is required for the service layer to associate the version with the correct
  // data structure, but it should not be provided by the client in the input DTO.
  private UUID dataStructureId;
}
