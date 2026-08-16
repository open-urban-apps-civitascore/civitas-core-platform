package de.civitascore.portal.model.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Self-contained dataset import bundle: the dataset shell plus the artifacts it ships. One call,
 * one transaction — the backend orchestrates the graph; the caller never has to replay the UI's
 * cascade of requests.
 *
 * <p>Accepts the dataset shell, data structures, data sources and mappings. The fields for the
 * remaining artifact types exist so that callers sending them get an explicit 400 ("not yet
 * supported") instead of silent dropping.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSetImportInputDTO extends BaseDataEntityInputDTO {

  @NotBlank(message = "Name is required and must be between 3 and 255 characters") @Size(min = 3, max = 255, message = "Name must be between 3 and 255 characters") private String name;

  private String description;

  @Size(max = 1024, message = "bundleId must not exceed 1024 characters") @Schema(
      description =
          "Catalogue identity of the bundle this import comes from, in whatever scheme the"
              + " catalogue uses. Recorded verbatim in the install provenance, never interpreted —"
              + " a bundle without catalogue identity simply installs without one.")
  private String bundleId;

  @Size(max = 255, message = "bundleVersion must not exceed 255 characters") @Schema(description = "Version of the catalogue bundle, recorded alongside bundleId")
  private String bundleVersion;

  @Schema(description = "Optional datapool the dataset belongs to")
  private java.util.UUID datapoolId;

  @Valid @Schema(description = "Data structures shipped with this bundle (created or reused by URN)")
  private List<DataStructureImportInputDTO> dataStructures = List.of();

  @Valid @Schema(description = "Data sources shipped with this bundle, referencing structures by URN")
  private List<DataSourceImportInputDTO> dataSources = List.of();

  @Valid @Schema(
      description =
          "Mappings shipped with this bundle, each stored under its own authored CORE URN and"
              + " referencing the structures it transforms between by URN")
  private List<MappingImportInputDTO> mappings = List.of();

  @Schema(description = "Not yet supported by the import — sending a non-empty list yields 400")
  private List<Object> pipelines;

  @Schema(description = "Not yet supported by the import — sending a non-empty list yields 400")
  private List<Object> dataSinks;
}
