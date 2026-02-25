package de.civitascore.portal.model.output.summary;

import de.civitascore.portal.model.output.BaseOutputDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataStructureVersionSummaryDTO extends BaseOutputDTO {
  private String version;
}
