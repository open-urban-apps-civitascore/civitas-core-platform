package de.civitascore.modelforge.domain;

/**
 * The public format and content-type tokens used across the API surface — a single source of truth
 * so a typo cannot silently mismatch a discriminator (e.g. a DTO format discriminator).
 */
public final class Formats {

    private Formats() {}

    /** Authored/served format discriminators. */
    public static final String JSON_SCHEMA = "jsonschema";
    public static final String XSD = "xsd";

    /** Media types for the served representations. */
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_XML = "application/xml";
}
