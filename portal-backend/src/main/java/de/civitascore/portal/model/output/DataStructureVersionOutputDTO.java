package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.DataStructureVersionSource;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.output.summary.DataStructureSummaryDTO;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataStructureVersionOutputDTO extends BaseOutputDTO {
  private String description;
  private String version;

  private DataStructureSummaryDTO dataStructure;

  private DataStructureVersionStatus dataStructureVersionStatus;
  private DataStructureVersionSource dataStructureVersionSource;

  private String modelAtlasUri;
  private String modelName;
  private Map<String, Object> styles;
}
