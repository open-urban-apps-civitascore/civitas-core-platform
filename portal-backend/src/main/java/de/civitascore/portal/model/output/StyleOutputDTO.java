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

  @Schema(description = "ID of the dataset this style belongs to")
  private UUID dataSetId;

  @Schema(description = "Unique name of the style within its dataset")
  private String name;

  @Schema(description = "SLD (Styled Layer Descriptor) XML content")
  private String sldContent;

  @Schema(
      description = "True if any Layer references this Style as its default or alternative Style")
  private boolean inUse;
}
