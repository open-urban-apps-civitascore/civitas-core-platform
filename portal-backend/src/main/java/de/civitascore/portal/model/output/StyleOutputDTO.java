package de.civitascore.portal.model.output;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO for Style entities. */
@Schema(description = "Style details")
@Data
@EqualsAndHashCode(callSuper = true)
public class StyleOutputDTO extends BaseOutputDTO {

  private UUID dataSetId;
  private String name;
  private String sldContent;

  @Schema(
      description = "True if any Layer references this Style as its default or alternative Style")
  private boolean inUse;
}
