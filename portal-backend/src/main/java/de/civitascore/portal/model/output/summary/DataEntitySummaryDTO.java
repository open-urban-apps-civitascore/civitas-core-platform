package de.civitascore.portal.model.output.summary;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Lightweight summary DTO for generic data entities, used as a polymorphic scope reference in
 * assignments.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataEntitySummaryDTO extends BaseSummaryNamedDTO {}
