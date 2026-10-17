package de.civitascore.portal.model.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * The package being installed: its declared identity and the artifacts it ships, with every
 * member's content inlined. This is the transport form — in a repository the same manifest
 * references one file per member instead.
 */
@Data
public class PackageManifestInputDTO {

  @NotBlank(message = "id is required") @Size(max = 1024, message = "id must not exceed 1024 characters") @Schema(
      description =
          "Identity of the package, recorded verbatim and never interpreted. Follows whatever"
              + " scheme the catalogue uses and need not be a CORE URN.")
  private String id;

  @Schema(description = "Version of the package, recorded alongside its identity")
  private String version;

  @Schema(description = "Display title of the package")
  private String title;

  @Valid @NotEmpty(message = "members is required") private List<PackageMemberInputDTO> members;
}
