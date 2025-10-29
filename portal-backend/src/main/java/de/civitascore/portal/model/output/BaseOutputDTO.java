package de.civitascore.portal.model.output;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Abstract base class for all output DTOs. Output DTOs contain the entity ID and audit timestamps
 * (createdAt, modifiedAt).
 *
 * @param <ID> the type of the entity ID (typically String for UUIDs)
 */
@Data
public abstract class BaseOutputDTO<ID extends Serializable> implements Serializable {

  protected ID id;
  protected LocalDateTime createdAt;
  protected LocalDateTime modifiedAt;
}
