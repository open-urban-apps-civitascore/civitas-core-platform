package de.civitascore.portal.model.output;

import de.civitascore.portal.model.output.summary.UserSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO representing a datapool for API responses. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataPoolOutputDTO extends BaseOutputDTO {

  @Schema(example = "City Mobility DataPool")
  private String name;

  @Schema(example = "Groups all mobility-related datasets under one governance boundary")
  private String description;

  @Schema(description = "User designated as the contact person for this datapool")
  private UserSummaryDTO contactPerson;

  @Schema(description = "UUIDs of datasets assigned to this datapool")
  private List<String> datasets = new ArrayList<>();
}
