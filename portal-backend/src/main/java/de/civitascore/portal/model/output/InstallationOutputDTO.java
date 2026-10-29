package de.civitascore.portal.model.output;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

  @Schema(
      description =
          "The Data pool of the Data sources and of the Dataset that the install created. Null when"
              + " the package has neither",
      example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
  private UUID datapoolId;

  @Schema(
      description = "The time of the uninstall. Null while the installation is active",
      accessMode = Schema.AccessMode.READ_ONLY,
      example = "2025-06-16T09:15:00")
  private LocalDateTime uninstalledAt;

  @Schema(description = "One line for each artifact that the install created")
  private List<InstalledArtifactOutputDTO> artifacts = new ArrayList<>();
}
