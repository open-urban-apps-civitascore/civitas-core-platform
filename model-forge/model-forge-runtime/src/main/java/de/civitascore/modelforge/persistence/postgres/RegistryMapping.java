package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.domain.Formats;

/**
 * Shared registry token constants and the legacy "group" → {@code artifact_type} mapping.
 *
 * <p>Functional category ({@code artifact_type}) and technical representation
 * ({@code format}) are stored separately: an XSD is {@code artifact_type = 'element'}
 * with a stored {@code format = 'xsd'} representation. The format is NEVER inferred from the
 * URN — it is read from the stored representations (see {@code ArtifactRegistry.formatsOf}).
 *
 * <p>The format/content-type tokens that also cross the HTTP boundary are owned by
 * {@link Formats} (the public api module); the aliases here just re-expose them to the registry
 * package so there is one definition. The {@code artifact_type} discriminators are registry-internal
 * and live only here.
 */
final class RegistryMapping {

    // ── Functional artifact types (the model_forge.artifact.artifact_type discriminator) ──────
    static final String TYPE_ELEMENT = "element";
    static final String TYPE_DATASET       = "dataset";
    static final String TYPE_MAPPING       = "mapping";
    static final String TYPE_PIPELINE      = "pipeline";
    static final String TYPE_DATASOURCE    = "datasource";
    static final String TYPE_DATASINK      = "datasink";
    static final String TYPE_DATASTRUCTURE       = "datastructure";

    // ── Technical representation formats (model_forge.artifact_representation.format) ──────────
    static final String FORMAT_JSONSCHEMA = Formats.JSON_SCHEMA;   // shared with the HTTP surface
    static final String FORMAT_CORE_JSON  = "core-json";            // registry-internal only
    static final String FORMAT_XSD         = Formats.XSD;           // shared with the HTTP surface

    // ── Representation content types (shared with the HTTP surface) ────────────────
    static final String CONTENT_TYPE_JSON = Formats.CONTENT_TYPE_JSON;
    static final String CONTENT_TYPE_XML  = Formats.CONTENT_TYPE_XML;

    private RegistryMapping() {}

    /**
     * The {@code artifact_type} that a legacy group name maps to, or the group string itself
     * when it is not a known group (so unknown groups simply match nothing).
     */
    static String artifactTypeForGroup(String group) {
        if (group == null) return "";
        return switch (group) {
            case "elements" -> TYPE_ELEMENT;
            case "mappings"       -> TYPE_MAPPING;
            case "pipelines"      -> TYPE_PIPELINE;
            case "datasources"    -> TYPE_DATASOURCE;
            case "datasinks"      -> TYPE_DATASINK;
            case "datasets"       -> TYPE_DATASET;
            case "datastructures"       -> TYPE_DATASTRUCTURE;
            default                -> group;
        };
    }
}
