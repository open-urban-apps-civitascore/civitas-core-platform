package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

/** Request to install one package on this instance. */
@Data
public class InstallationInputDTO {

  @Schema(
      description =
          "The Data pool of the Data sources and of the Dataset of the package. It is necessary"
              + " when the package has a member of the kind datasource or dataset.")
  private UUID datapoolId;

  @Valid @NotNull(message = "package is required") @JsonProperty("package")
  @Schema(description = "The package to install")
  private PackageManifestInputDTO packageManifest;
}
