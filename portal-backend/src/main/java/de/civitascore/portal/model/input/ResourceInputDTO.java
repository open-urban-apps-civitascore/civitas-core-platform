package de.civitascore.portal.model.input;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceInputDTO extends BaseInputDTO {
  // Resource only has BaseEntity fields, so no additional fields needed
}
