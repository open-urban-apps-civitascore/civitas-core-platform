package de.civitascore.portal.model.input;

import de.civitascore.portal.model.input.assignment.AssignmentScopedInputDTO;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseDataEntityInputDTO extends BaseInputDTO {
  @Valid private Set<AssignmentScopedInputDTO> assignments;
}
