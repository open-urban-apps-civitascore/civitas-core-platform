package de.civitascore.portal.model.output;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** A Mapping as the list of the Mappings of a Dataset names it. */
@Schema(description = "A Mapping of a Dataset")
public record MappingSummaryDTO(
    @Schema(description = "UUID of the CORE URN of the Mapping") UUID mappingId,
    @Schema(description = "CORE URN of the Mapping without version") String logicalUrn,
    String title) {}
