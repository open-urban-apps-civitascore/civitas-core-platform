package de.civitascore.portal.model.output.summary;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Lightweight summary DTO for data space entities, used in list endpoints and nested references.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSpaceSummaryDTO extends BaseSummaryNamedDTO {}
