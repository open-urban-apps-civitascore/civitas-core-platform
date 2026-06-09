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
 *   <li>{@code dataStructure} — the resolved schema content for the referenced data-structure
 *       version, as currently served by Model Atlas (XML for now, JSON schema later). {@code null}
 *       when no version is referenced or it cannot be resolved.
 * </ul>
 */
public record DataSinkPayload(
    String id, String type, Map<String, Object> configuration, String dataStructure) {}
