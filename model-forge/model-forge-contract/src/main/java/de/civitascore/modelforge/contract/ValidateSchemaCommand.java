package de.civitascore.modelforge.contract;

import tools.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * Command to validate one JSON Schema document — the schema-only counterpart to
 * {@link ValidateInstanceCommand}.
 */
public record ValidateSchemaCommand(JsonNode schema) {

    public ValidateSchemaCommand {
        Objects.requireNonNull(schema, "schema");
    }
}
