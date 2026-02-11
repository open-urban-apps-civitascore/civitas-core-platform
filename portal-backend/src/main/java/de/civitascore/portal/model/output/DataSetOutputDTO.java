package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.output.summary.AgentSummaryDTO;
import de.civitascore.portal.model.output.summary.CatalogSummaryDTO;
import de.civitascore.portal.model.output.summary.DataSetSeriesSummaryDTO;
import de.civitascore.portal.model.output.summary.DataSpaceSummaryDTO;
import de.civitascore.portal.model.output.summary.DistributionSummaryDTO;
import de.civitascore.portal.model.output.summary.PipelineSummaryDTO;
import de.civitascore.portal.model.output.summary.UserSummaryDTO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataSetOutputDTO extends BaseOutputDTO {

  private String identifier;
  private String name;
  private String description;
  private String title;

  private DataSetStatus dataSetStatus;
  private String version;

  private UserSummaryDTO owner;
  private DataSetSeriesSummaryDTO dataSetSeries;
  private List<DataSpaceSummaryDTO> dataSpaces;
  private List<AgentSummaryDTO> agents;
  private List<DistributionSummaryDTO> distributions;
  private List<CatalogSummaryDTO> catalogs;
  private List<PipelineSummaryDTO> pipelines;

  private Long persistenceId;
  private String externalId;
  private String format;
  private Boolean openDataAccess;
}
