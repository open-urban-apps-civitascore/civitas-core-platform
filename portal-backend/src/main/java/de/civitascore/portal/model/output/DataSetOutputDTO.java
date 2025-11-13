package de.civitascore.portal.model.output;

import de.civitascore.portal.model.output.summary.DataSpaceSummaryDTO;
import de.civitascore.portal.model.output.summary.UserSummaryDTO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataSetOutputDTO extends BaseOutputDTO {
  private String name;
  private String description;
  private UserSummaryDTO owner;
  private List<DataSpaceSummaryDTO> dataSpaces;
  private String externalId;
  private String format;
}
