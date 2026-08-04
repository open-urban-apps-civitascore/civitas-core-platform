package de.civitascore.portal.model.output.summary;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Lightweight summary DTO for data source entities, used in list endpoints and nested references.
 *
 * <p>Carries id and name only. Connector configuration stays out deliberately: this is what {@code
 * GET /datasets/{id}/usable-datasources} returns to a caller authorized on the dataset rather than
 * on the data source.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSourceSummaryDTO extends BaseSummaryNamedDTO {}
