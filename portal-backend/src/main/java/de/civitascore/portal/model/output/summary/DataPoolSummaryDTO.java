package de.civitascore.portal.model.output.summary;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Lightweight summary DTO for datapool entities, used in nested references on data entities. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataPoolSummaryDTO extends BaseSummaryNamedDTO {}
