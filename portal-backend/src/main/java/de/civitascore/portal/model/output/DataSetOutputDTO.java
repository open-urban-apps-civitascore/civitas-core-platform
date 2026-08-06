package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.output.summary.DataPoolSummaryDTO;
import de.civitascore.portal.model.output.summary.PipelineSummaryDTO;
import de.civitascore.portal.model.output.summary.UserSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO representing a dataset for API responses. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSetOutputDTO extends BaseOutputDTO {

  @Schema(example = "traffic-count-2025")
  private String identifier;

  @Schema(example = "Traffic Count 2025")
  private String name;

  @Schema(example = "Hourly vehicle counts at major intersections")
  private String description;

  @Schema(example = "DRAFT")
  private DataSetStatus dataSetStatus;

  @Schema(example = "1.0.0")
  private String version;

  private List<PipelineSummaryDTO> pipelines = new ArrayList<>();

  @Schema(
      description =
          "Named API endpoints exposed by this dataset (per concepts #1379 and #1383). Each"
              + " entry's previewUrl is server-populated from the configured data-plane domain.")
  private List<NamedApiOutputDTO> namedApis = new ArrayList<>();

  @Schema(description = "Whether this dataset is publicly accessible")
  private Boolean openDataAccess;

  @Schema(description = "Datapool this dataset is assigned to")
  private DataPoolSummaryDTO datapool;

  private UserSummaryDTO createdBy;

  @Schema(
      description =
          "Public APISIX-fronted URL for this dataset, populated after a successful release saga",
      accessMode = Schema.AccessMode.READ_ONLY,
      example = "https://api.core.civitasconnect.digital/datasets/traffic-count-2025")
  private String publicUrl;

  @Schema(
      description =
          "Type of saga currently in progress (CREATE, UPDATE, UNRELEASE, DELETE), or null when"
              + " idle",
      accessMode = Schema.AccessMode.READ_ONLY)
  private PendingSagaType pendingSagaType;

  @Schema(
      description =
          "Whether a provisioning saga has completed successfully, so whatever sinks the dataset"
              + " carried at release physically exist (a PostGIS table / FROST project). Stays true"
              + " across an unrelease. Used to warn before a destructive sink change that would"
              + " rebuild the table.",
      accessMode = Schema.AccessMode.READ_ONLY)
  private Boolean provisioned;
}
