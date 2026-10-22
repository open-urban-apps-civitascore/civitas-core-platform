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
          "The identity of the member in the package. The other members reference the member by"
              + " this identity, and the journal keeps it as the origin of the copy. It does not"
              + " have to be a CORE URN.")
  private String urn;

  @Size(max = InstalledArtifact.NAME_LENGTH, message = "name must not exceed {max} characters") @Schema(
      description =
          "The display name. When it is empty, the install uses the title in the document, or"
              + " the name in the document of a named API.",
      example = "Tree cadastre")
  private String name;

  @Schema(
      description =
          "The description of the copy. When it is empty, the install uses the description in"
              + " the document. A Data structure, a Data source and a Dataset need one of the two.")
  private String description;

  @NotEmpty(message = "content is required") @Schema(
      description =
          "The CORE document of the member, for example the JSON Schema of a Data structure or"
              + " the document of a Data source. The backend gives each copy a URN of this"
              + " instance. In a Data structure, it replaces the $id of the document and of its"
              + " $defs members.")
  private Map<String, Object> content;
}
