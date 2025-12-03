package de.civitascore.portal.model.output;

import de.civitascore.portal.model.output.summary.CatalogSummaryDTO;
import de.civitascore.portal.model.output.summary.DataSetSummaryDTO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CatalogOutputDTO extends BaseOutputDTO {
  private String name;
  private String description;
  private List<CatalogSummaryDTO> childCatalogs;
  private List<CatalogSummaryDTO> parentCatalogs;
  private List<DataSetSummaryDTO> dataSets;
}
