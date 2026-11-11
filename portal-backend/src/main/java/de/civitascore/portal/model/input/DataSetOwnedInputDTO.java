package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Abstract base input DTO for entities nested under a parent dataset. {@code DataSetOwnedService}
 * sets {@code dataSetId} from the parent it is given, so a request body cannot set it.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class DataSetOwnedInputDTO extends BaseInputDTO {

  @JsonIgnore private UUID dataSetId;
}
