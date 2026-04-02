package de.civitascore.portal.model.output.summary;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Lightweight summary DTO for group entities, used in list endpoints and nested references. */
@Data
@EqualsAndHashCode(callSuper = true)
public class GroupSummaryDTO extends BaseSummaryNamedDTO {

  private String description;
}
