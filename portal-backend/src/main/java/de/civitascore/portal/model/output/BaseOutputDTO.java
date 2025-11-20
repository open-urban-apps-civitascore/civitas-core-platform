package de.civitascore.portal.model.output;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;

/**
 * Abstract base class for all output DTOs. Output DTOs contain the entity ID and audit timestamps
 * (createdAt, modifiedAt).
 */
@Data
public abstract class BaseOutputDTO implements Serializable {

  protected UUID id;
  protected LocalDateTime createdAt;
  protected LocalDateTime modifiedAt;
}
