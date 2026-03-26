package de.civitascore.portal.model.output.summary;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Lightweight summary DTO for dataset series entities, used in list endpoints and nested
 * references.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSetSeriesSummaryDTO extends BaseSummaryNamedDTO {}
