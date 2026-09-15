package de.civitascore.portal.model.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

/**
 * One artifact inside an installable package: what it is, the identity it brings, and the CORE
 * document itself.
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
          "Logical CORE URN this member is installed under"
              + " (urn:core:<scope>:<owner>:<type>:<domain>:<name>:<disambiguator>). The package has"
              + " to bring it: without a declared identity the registry mints one, so the same"
              + " package would resolve to different URNs on every instance and its internal"
              + " references would not resolve at all.")
  private String urn;

  @Schema(description = "Display name, recorded in the install provenance")
  private String name;

  private String description;

  @NotEmpty(message = "content is required") @Schema(
      description =
          "The CORE document. Do not author '$schema' — the registry stamps it for the opaque"
              + " kinds. Where the document carries its own identity it must agree with 'urn'.")
  private Map<String, Object> content;
}
