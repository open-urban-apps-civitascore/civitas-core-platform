package de.civitascore.portal.model.output;

import de.civitascore.portal.model.output.summary.DataSpaceSummaryDTO;
import de.civitascore.portal.model.output.summary.UserSummaryDTO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataSpaceOutputDTO extends BaseOutputDTO<String> {
  private String title;
  private String description;
  private UserSummaryDTO owner;
  private DataSpaceSummaryDTO parentDataSpace;
  private List<DataSpaceSummaryDTO> childDataSpaces;
  private String externalId;
  private String metadata;
  private String tenantId;
}
