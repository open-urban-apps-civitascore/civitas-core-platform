package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.output.summary.DataStructureVersionSummaryDTO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataStructureOutputDTO extends BaseOutputDTO {
  private String name;
  private String description;
  private DataStructureStatus dataStructureStatus;
  private Boolean createdFromDataSource;
  private List<DataStructureVersionSummaryDTO> dataStructureVersions;
}
