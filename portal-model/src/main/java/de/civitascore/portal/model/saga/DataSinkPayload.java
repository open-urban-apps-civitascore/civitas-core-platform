package de.civitascore.portal.model.saga;

import java.util.Map;

/**
 * A pipeline output sink carried in the dataset saga trigger payload, mirroring how datasources
 * ride at the dataset level. Because the pipeline {@code model} references its sink only by id, the
 * config-adapter needs these resolved fields to build the engine flow's write target without
 * calling back to the backend.
 *
 * <p>This type lives in portal-model so it can be shared by both the portal-backend (which produces
 * the trigger) and the config-adapter (which consumes it).
 *
 * <ul>
 *   <li>{@code configuration} — the type-specific settings stored on the {@code DataSink} entity
 *       (for {@code POSTGIS}: {@code tableName} and {@code dataStructureVersionId}; empty/absent
 *       for {@code FROST}).
 *   <li>{@code dataStructure} — the referenced data-structure version's model (a JSON Schema
 *       document) persisted directly on the {@code DataStructureVersion}, carried as a JSON object.
 *       {@code null} only when the sink references no data-structure version (e.g. FROST sinks). If
 *       a version <em>is</em> referenced but carries no model, the saga publish fails rather than
 *       emitting a null value here.
 * </ul>
 */
public record DataSinkPayload(
    String id, String type, Map<String, Object> configuration, Map<String, Object> dataStructure) {}
