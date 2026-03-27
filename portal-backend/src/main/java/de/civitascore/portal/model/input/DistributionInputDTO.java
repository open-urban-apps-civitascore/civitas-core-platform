package de.civitascore.portal.model.input;

import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating distribution resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DistributionInputDTO extends BaseInputDTO {
  private String accessUrl;
  private UUID resourceId;
}
