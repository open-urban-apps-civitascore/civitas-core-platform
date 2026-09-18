package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;

/**
 * Request to install one package on this instance: what to install, and the instance-specific
 * values to fill its parameters with.
 *
 * <p>The two are deliberately separate. The package is the portable part and is identical on every
 * instance; the parameters are what differs between them — a broker address, a host name. Resolving
 * them happens here, before anything reaches the registry, because the registry holds the documents
 * the config adapter deploys and must never hold a template.
 */
@Data
public class InstallationInputDTO {

  @Valid @NotNull(message = "package is required") @JsonProperty("package")
  private PackageManifestInputDTO packageManifest;

  @Schema(
      description =
          "Values for the parameters the package declares. Accepted but not yet applied —"
              + " parameter resolution is the next increment; until then a package has to carry"
              + " concrete values.")
  private Map<String, Object> parameters;
}
