package de.civitascore.portal.model.output.summary;

import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Abstract base class for named summary DTOs, extending {@link BaseSummaryDTO} with a name. */
@EqualsAndHashCode(callSuper = true)
@Data
public abstract class BaseSummaryNamedDTO extends BaseSummaryDTO implements Serializable {

  private String name;
}
