package de.civitascore.portal.model.input;

import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DistributionInputDTO extends BaseInputDTO {
  private String accessUrl;
  private UUID resourceId;
}
