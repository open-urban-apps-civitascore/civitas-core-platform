package de.civitascore.portal.model.output;

import de.civitascore.portal.model.output.summary.DataSpaceSummaryDTO;
import de.civitascore.portal.model.output.summary.UserSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO representing a data space for API responses. */
@Schema(description = "Data space details")
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSpaceOutputDTO extends BaseOutputDTO {
  private String name;
  private String description;
  private UserSummaryDTO owner;
  private DataSpaceSummaryDTO parentDataSpace;
  private List<DataSpaceSummaryDTO> childDataSpaces = new ArrayList<>();
  private String externalId;
}
