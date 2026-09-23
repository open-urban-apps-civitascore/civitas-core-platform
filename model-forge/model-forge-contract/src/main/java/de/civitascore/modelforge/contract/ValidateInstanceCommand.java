package de.civitascore.modelforge.contract;

import tools.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * Command to validate one JSON instance against one schema.
 */
public record ValidateInstanceCommand(JsonNode schema, JsonNode instance) {

    public ValidateInstanceCommand {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(instance, "instance");
    }
}
