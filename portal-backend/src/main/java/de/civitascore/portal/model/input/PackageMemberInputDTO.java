package de.civitascore.portal.model.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

/**
 * One artifact inside an installable package: what it is, the identity it has inside the package,
 * and the CORE document itself.
 *
 * <p>The package identity never becomes an identity here. The install mints a URN of this instance
 * for every member and records the package URN as the copy's origin; inside the package the URN
 * only serves to let members refer to one another, and every such reference is rewritten to the
 * minted URN before the referring member is stored.
 *
 * <p>The install order is not taken from this list — it is derived from the references the members
 * declare, so a package cannot break by listing its members in an inconvenient order.
 */
@Data
public class PackageMemberInputDTO {

  @NotNull(message = "kind is required") @Schema(description = "Artifact kind, in the vocabulary of the URN's artifact-type segment")
  private PackageMemberKind kind;

  @NotBlank(message = "urn is required") @Size(max = 1024, message = "urn must not exceed 1024 characters") @Schema(
      description =
          "The member's identity inside the package, by convention a logical CORE URN"
              + " (urn:core:<scope>:<owner>:<type>:<domain>:<name>:<disambiguator>). Recorded as"
              + " the installed copy's origin and used to resolve references between members; the"
              + " instance mints the URN the copy is installed under.")
  private String urn;

  @Schema(description = "Display name, recorded in the install provenance")
  private String name;

  private String description;

  @NotEmpty(message = "content is required") @Schema(
      description =
          "The CORE document. Do not author '$schema' — the registry stamps it for the opaque"
              + " kinds. Identities inside it ('$id' on the root and on '$defs' members) are"
              + " package-local: the install replaces them with minted URNs and remembers the"
              + " originals as origin.")
  private Map<String, Object> content;
}
