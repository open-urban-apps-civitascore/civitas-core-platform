package de.civitascore.portal.model.output;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/**
 * One installation as recorded: which package, and what it did to every artifact it touched. The
 * lines are returned with the installation so a caller never needs a second request to learn what
 * was created.
 */
@Data
public class InstallationOutputDTO {

  private UUID id;
  private String packageId;
  private String packageVersion;

  /** The dataset this install produced, if any. */
  private UUID dataSetId;

  private String dataSetName;

  private LocalDateTime createdAt;
  private UUID createdBy;

  /**
   * The time of the uninstall. Null while the installation is active. The artifact lines of an
   * uninstalled installation say what it created, not what exists.
   */
  private LocalDateTime uninstalledAt;

  private List<InstalledArtifactOutputDTO> artifacts = new ArrayList<>();
}
