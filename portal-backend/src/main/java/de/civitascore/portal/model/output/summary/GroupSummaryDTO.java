package de.civitascore.portal.model.output.summary;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GroupSummaryDTO extends BaseSummaryNamedDTO {

  private String description;
}
