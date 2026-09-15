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

  /** Logical CORE URN — the stable identity other packages resolve against. */
  private String urn;

  /** The concrete version this install created or reused. */
  private String versionedUrn;

  private InstalledArtifactAction action;
}
