package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.output.summary.DistributionSummaryDTO;
import de.civitascore.portal.model.output.summary.PipelineSummaryDTO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataSetOutputDTO extends BaseOutputDTO {

  private String identifier;
  private String name;
  private String description;

  private DataSetStatus dataSetStatus;
  private String version;

  private List<PipelineSummaryDTO> pipelines;
  private List<DistributionSummaryDTO> distributions;

  private Boolean openDataAccess;

  /** The public APISIX-fronted URL for this dataset, populated after a successful CREATE saga. */
  private String publicUrl;

  /**
   * The type of saga currently in progress, or {@code null} when no saga is running. Clients can
   * use this to display a provisioning/teardown indicator in the UI.
   */
  private PendingSagaType pendingSagaType;
}
