package de.civitascore.portal.model.output.summary;

import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public abstract class BaseSummaryNamedDTO extends BaseSummaryDTO implements Serializable {
  private String name;
}
