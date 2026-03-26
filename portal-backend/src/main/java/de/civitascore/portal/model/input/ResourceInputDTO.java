package de.civitascore.portal.model.input;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating resource entities. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceInputDTO extends BaseInputDTO {
  // Resource only has BaseEntity fields, so no additional fields needed
}
