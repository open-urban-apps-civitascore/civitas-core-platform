package de.civitascore.portal.model.output.summary;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** A version row of a data structure, with how the version is used. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataStructureVersionUsageSummaryDTO extends DataStructureVersionSummaryDTO {

  @Schema(
      description =
          "Whether anything references this version, released or draft. While true, it cannot be"
              + " deleted",
      accessMode = Schema.AccessMode.READ_ONLY)
  private boolean inUse;

  @Schema(
      description =
          "Whether a released entity references this version. While true, it cannot be"
              + " unreleased and its model is locked",
      accessMode = Schema.AccessMode.READ_ONLY)
  private boolean inUseByReleased;
}
