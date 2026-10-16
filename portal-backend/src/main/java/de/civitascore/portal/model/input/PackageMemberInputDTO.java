package de.civitascore.portal.model.input;

import de.civitascore.portal.model.entity.InstalledArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

/**
 * One member of a package: its kind, its identity in the package and its CORE document. The install
 * creates a copy of the member on this instance. The backend gives the copy its own URN, and the
 * journal keeps the identity in the package as the origin of the copy.
 */
@Data
public class PackageMemberInputDTO {

  @NotNull(message = "kind is required") @Schema(description = "The kind of the member", example = "datastructure")
  private PackageMemberKind kind;

  @NotBlank(message = "urn is required") @Size(max = InstalledArtifact.URN_LENGTH, message = "urn must not exceed {max} characters") @Schema(
      description =
          "The identity of the member in the package. The journal keeps it as the origin of the"
              + " copy. It does not have to be a CORE URN.")
  private String urn;

  @Size(max = InstalledArtifact.NAME_LENGTH, message = "name must not exceed {max} characters") @Schema(
      description =
          "The display name. When it is empty, the install uses the title of the document.",
      example = "Tree cadastre")
  private String name;

  @Schema(
      description =
          "The description of the Data structure. When it is empty, the install uses the"
              + " description in the document. One of the two is necessary.")
  private String description;

  @NotEmpty(message = "content is required") @Schema(
      description =
          "The CORE document of the member: the JSON Schema of a Data structure. The backend"
              + " replaces the $id of the document and of its $defs members with URNs of this"
              + " instance.")
  private Map<String, Object> content;
}
