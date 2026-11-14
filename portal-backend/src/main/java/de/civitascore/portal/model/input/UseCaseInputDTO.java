package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

/** Request to install one package on this instance. */
@Data
public class UseCaseInputDTO {

  @Schema(
      description =
          "The Data pool of the Data sources and of the Dataset of the package. It is necessary"
              + " when the package has a member of the kind datasource or dataset. A Data pool can"
              + " have only one active use case of a package. Under the path of a Data pool, the"
              + " request can leave it out. If it is there, it must be the Data pool of the path.")
  private UUID datapoolId;

  @Valid @NotNull(message = "package is required") @JsonProperty("package")
  @Schema(description = "The package to install")
  private PackageManifestInputDTO packageManifest;
}
