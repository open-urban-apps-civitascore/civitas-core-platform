package de.civitascore.modelforge.contract;

import tools.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * Command to import one JSON Schema document into Model Forge. The import stores the Elements
 * (splitting {@code $defs}) plus their automatic DataStructure grouping — composition into a
 * DataSet is the caller's concern: update the DataSet manifest via
 * {@link de.civitascore.modelforge.facade.ModelForge#saveArtifact(SaveArtifactCommand)}.
 *
 * @param schema the JSON Schema document (required)
 */
public record ImportSchemaCommand(JsonNode schema) {

    public ImportSchemaCommand {
        Objects.requireNonNull(schema, "schema");
    }
}
