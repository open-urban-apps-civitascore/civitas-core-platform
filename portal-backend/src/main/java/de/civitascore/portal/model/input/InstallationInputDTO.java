package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Request to install one package on this instance. */
@Data
public class InstallationInputDTO {

  @Valid @NotNull(message = "package is required") @JsonProperty("package")
  @Schema(description = "The package to install")
  private PackageManifestInputDTO packageManifest;
}
