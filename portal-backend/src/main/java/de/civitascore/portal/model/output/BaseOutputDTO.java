package de.civitascore.portal.model.output;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Abstract base class for all output DTOs. Output DTOs contain the entity ID and audit timestamps
 * (createdAt, modifiedAt).
 *
 * @param the type of the entity ID (typically String for UUIDs)
 */
@Data
public abstract class BaseOutputDTO implements Serializable {

  protected String id;
  protected LocalDateTime createdAt;
  protected LocalDateTime modifiedAt;
}
