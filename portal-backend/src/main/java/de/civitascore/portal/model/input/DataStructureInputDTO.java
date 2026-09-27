package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Input DTO for creating and updating data structure resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataStructureInputDTO extends DataStructureMetaInputDTO {

  @JsonIgnore private DataStructureStatus dataStructureStatus;
}
