package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.output.summary.DataStructureSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

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

  @Schema(description = "URI to the model in the atlas", accessMode = Schema.AccessMode.READ_ONLY)
  private String modelAtlasUri;

  private String modelName;
  private Map<String, Object> styles;

  @Schema(description = "Data model definition (JSON schema)")
  private String model;

  @Schema(
      description = "Whether this version is currently referenced by a data source",
      accessMode = Schema.AccessMode.READ_ONLY)
  private boolean inUse;
}
