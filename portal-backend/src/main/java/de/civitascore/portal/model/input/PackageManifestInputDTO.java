package de.civitascore.portal.model.input;

import de.civitascore.portal.model.entity.Installation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * The package to install: its identity, its version and its members. The request contains the
 * content of each member.
 */
@Data
public class PackageManifestInputDTO {

  @NotBlank(message = "id is required") @Size(max = Installation.PACKAGE_ID_LENGTH, message = "id must not exceed {max} characters") @Schema(
      description =
          "The identity of the package. The journal keeps it as it is and does not interpret it."
              + " It does not have to be a CORE URN.",
      example = "tree-cadastre")
  private String id;

  @Size(
      max = Installation.PACKAGE_VERSION_LENGTH,
      message = "version must not exceed {max} characters")
  @Schema(description = "The version of the package", example = "1.0.0")
  private String version;

  @Valid @NotEmpty(message = "members is required") @Schema(description = "The members of the package")
  private List<PackageMemberInputDTO> members;
}
