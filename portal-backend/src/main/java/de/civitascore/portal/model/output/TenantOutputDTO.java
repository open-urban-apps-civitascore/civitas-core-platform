package de.civitascore.portal.model.output;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TenantOutputDTO extends BaseOutputDTO {
  private String name;
  private String description;
  private Boolean active;
  private String externalId;
  private String settings;
}
