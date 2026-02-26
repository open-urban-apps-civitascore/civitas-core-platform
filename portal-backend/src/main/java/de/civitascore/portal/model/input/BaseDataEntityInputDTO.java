package de.civitascore.portal.model.input;

import jakarta.validation.Valid;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseDataEntityInputDTO extends BaseInputDTO {
  @Valid private List<AssignmentScopedInputDTO> assignments;
}
