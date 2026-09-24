package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.output.summary.DataStructureSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO representing a data structure version for API responses. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataStructureVersionOutputDTO extends BaseOutputDTO {

  @Schema(example = "Initial version of traffic sensor schema")
  private String description;

  @Schema(example = "1.0.0")
  private String version;

  private DataStructureSummaryDTO dataStructure;

  private DataStructureVersionStatus dataStructureVersionStatus;

  @Schema(description = "How this version was created (e.g. MANUAL, AUTO)")
  private DataStructureVersionSource dataStructureVersionSource;

  private String modelName;

  /** Versioned CORE URN of this version's model (DataStructure) artifact in Model Forge. */
  private String modelUrn;

  private Map<String, Object> styles;

  @Schema(description = "Data model definition as a JSON Schema document")
  private Map<String, Object> model;

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
