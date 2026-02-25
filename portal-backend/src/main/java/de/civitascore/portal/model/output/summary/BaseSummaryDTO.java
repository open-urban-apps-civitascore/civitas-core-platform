package de.civitascore.portal.model.output.summary;

import java.io.Serializable;
import java.util.UUID;
import lombok.Data;

@Data
public abstract class BaseSummaryDTO implements Serializable {
  private UUID id;
}
