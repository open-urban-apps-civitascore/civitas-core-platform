package de.civitascore.portal.model.output;

import de.civitascore.portal.model.output.summary.CatalogSummaryDTO;
import de.civitascore.portal.model.output.summary.DataSetSummaryDTO;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CatalogOutputDTO extends BaseOutputDTO {
  private String name;
  private String description;
  private List<CatalogSummaryDTO> childCatalogs = new ArrayList<>();
  private List<CatalogSummaryDTO> parentCatalogs = new ArrayList<>();
  private List<DataSetSummaryDTO> dataSets = new ArrayList<>();
}
