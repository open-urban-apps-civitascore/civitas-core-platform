package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.InstalledArtifactAction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Result summary of a dataset bundle import: the created dataset shell plus what happened to each
 * contained artifact (created vs. reused by URN identity).
 *
 * <p>Plain {@code @Data} rather than {@code @Builder}: every OutputDTO in this package has to offer
 * a no-arg constructor and non-null collections (pinned by {@code
 * OutputDtoCollectionInitializationTest}), and combining that with a builder invites the Lombok
 * trap where a builder-created instance skips the field initializers.
 */
@Data
public class DataSetImportOutputDTO {

  private UUID dataSetId;
  private String dataSetName;

  @Schema(description = "Id of the provenance record written for this install")
  private UUID installationId;

  private List<ImportedArtifactDTO> dataStructures = new ArrayList<>();
  private List<ImportedArtifactDTO> dataSources = new ArrayList<>();

  @Schema(
      description =
          "Mappings of this install. 'id' is null — a mapping has no host shell, it exists only as a"
              + " registry artifact identified by its URN.")
  private List<ImportedArtifactDTO> mappings = new ArrayList<>();

  @Data
  @Builder
  public static class ImportedArtifactDTO {
    private String name;
    private UUID id;

    @Schema(description = "Logical CORE URN, where the artifact type carries one")
    private String urn;

    @Schema(description = "What this import did with the artifact")
    private InstalledArtifactAction action;
  }
}
