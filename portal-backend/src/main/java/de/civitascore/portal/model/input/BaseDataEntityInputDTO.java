package de.civitascore.portal.model.input;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseDataEntityInputDTO extends BaseInputDTO {
  private List<AssignmentScopedInputDTO> assignments;
}
