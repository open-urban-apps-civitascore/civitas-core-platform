package de.civitascore.portal.model.output;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Result summary of a dataset bundle import: the created dataset shell plus what happened to each
 * contained artifact (created vs. reused by URN identity).
 */
@Data
@Builder
public class DataSetImportOutputDTO {

  private UUID dataSetId;
  private String dataSetName;

  private List<ImportedArtifactDTO> dataStructures;
  private List<ImportedArtifactDTO> dataSources;

  @Data
  @Builder
  public static class ImportedArtifactDTO {
    private String name;
    private UUID id;

    @Schema(description = "Logical CORE URN, where the artifact type carries one")
    private String urn;

    @Schema(description = "CREATED (new in this import) or REUSED (already installed, linked)")
    private String action;
  }
}
