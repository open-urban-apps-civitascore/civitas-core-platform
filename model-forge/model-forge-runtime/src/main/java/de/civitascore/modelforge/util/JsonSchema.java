package de.civitascore.modelforge.util;

import tools.jackson.databind.JsonNode;

/**
 * Shared JSON Schema constants.
 */
public final class JsonSchema {

    /** The JSON Schema 2020-12 dialect URI ({@code $schema} value). */
    public static final String DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";

    private JsonSchema() {}

    /** The document's {@code title} when it is non-blank text, else {@code null}. */
    public static String usableTitle(JsonNode document) {
        JsonNode title = document.path("title");
        return title.isTextual() && !title.asText().isBlank() ? title.asText() : null;
    }
}
