package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * One bundle install as recorded at import time: header plus one line per touched artifact. The
 * install timestamp is the inherited {@code createdAt}; the actor is exposed as {@code
 * installedBy}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InstallationOutputDTO extends BaseOutputDTO {

  @Schema(description = "Catalogue identity of the bundle, as declared by the installing caller")
  private String bundleId;

  private String bundleVersion;

  private UUID dataSetId;
  private String dataSetName;

  @Schema(description = "User id the install ran as (audit created_by)")
  private UUID installedBy;

  private List<InstalledArtifactOutputDTO> artifacts = new ArrayList<>();

  @Data
  @Builder
  public static class InstalledArtifactOutputDTO {
    private InstalledArtifactType artifactType;
    private String name;
    private UUID shellId;

    @Schema(description = "Logical CORE URN, where the artifact type carries one")
    private String urn;

    @Schema(description = "What the install did with the artifact")
    private InstalledArtifactAction action;
  }
}
