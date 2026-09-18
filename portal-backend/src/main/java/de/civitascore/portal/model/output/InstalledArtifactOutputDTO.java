package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import de.civitascore.portal.model.embedded.InstalledArtifactType;
import java.util.UUID;
import lombok.Data;

/** One artifact line of an installation: what it is, where it ended up, and what happened to it. */
@Data
public class InstalledArtifactOutputDTO {

  private UUID id;
  private InstalledArtifactType artifactType;
  private String name;

  /** Id of the shell row, where the artifact type has one. */
  private UUID shellId;

  /** Logical CORE URN of the copy on this instance, minted by the install. */
  private String urn;

  /** The concrete version this install created or linked. */
  private String versionedUrn;

  /** The URN the artifact carried in the package — where this copy came from. */
  private String origin;

  private InstalledArtifactAction action;
}
