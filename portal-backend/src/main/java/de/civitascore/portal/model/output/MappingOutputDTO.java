package de.civitascore.portal.model.output;

import de.civitascore.portal.model.input.MappingNodePosition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.UUID;

/** A version of a Mapping with its content. */
@Schema(description = "A version of a Mapping with its content")
public record MappingOutputDTO(
    @Schema(description = "UUID of the CORE URN of the Mapping") UUID mappingId,
    @Schema(description = "CORE URN of the Mapping without version") String logicalUrn,
    @Schema(description = "CORE URN of this version") String versionedUrn,
    String version,
    String title,
    String description,
    @Schema(description = "Versioned CORE URN of the source DataStructure") String source,
    @Schema(description = "Versioned CORE URN of the target DataStructure") String target,
    @Schema(
            description =
                "Rules per target field path, as the CORE Mapping schema mapping.schema.json"
                    + " defines them")
        Map<String, Object> fields,
    Map<String, MappingNodePosition> positions) {}
