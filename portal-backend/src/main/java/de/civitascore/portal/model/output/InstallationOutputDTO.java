package de.civitascore.portal.model.output;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Output DTO for one installation: the package and the artifact lines. The lines come with the
 * installation, so a caller needs no second request to see what the install created.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InstallationOutputDTO extends BaseOutputDTO {

  @Schema(
      description = "The identity of the package, as the caller gave it",
      example = "tree-cadastre")
  private String packageId;

  @Schema(description = "The version of the package", example = "1.0.0")
  private String packageVersion;

  @Schema(description = "One line for each artifact that the install created")
  private List<InstalledArtifactOutputDTO> artifacts = new ArrayList<>();
}
