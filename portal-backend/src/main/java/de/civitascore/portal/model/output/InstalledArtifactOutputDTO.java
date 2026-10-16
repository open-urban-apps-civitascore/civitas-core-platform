package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.InstalledArtifactType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO for one artifact line of an installation. */
@Data
@EqualsAndHashCode(callSuper = true)
public class InstalledArtifactOutputDTO extends BaseOutputDTO {

  @Schema(example = "DATA_STRUCTURE")
  private InstalledArtifactType artifactType;

  @Schema(example = "Tree cadastre")
  private String name;

  @Schema(description = "The id of the shell row, for example the id of the Data structure")
  private UUID shellId;

  @Schema(description = "The logical CORE URN of the copy on this instance")
  private String urn;

  @Schema(description = "The versioned CORE URN of the copy")
  private String versionedUrn;

  @Schema(description = "The identity of the artifact in the package")
  private String origin;
}
