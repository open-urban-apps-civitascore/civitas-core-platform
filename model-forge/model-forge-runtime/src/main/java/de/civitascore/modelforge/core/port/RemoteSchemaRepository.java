package de.civitascore.modelforge.core.port;

import tools.jackson.databind.JsonNode;

/**
 * Fetches external JSON schemas for import without exposing HTTP client details to application code.
 */
public interface RemoteSchemaRepository {

    JsonNode fetchJson(String url);
}
