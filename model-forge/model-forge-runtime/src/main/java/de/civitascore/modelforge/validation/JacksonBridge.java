package de.civitascore.modelforge.validation;

/**
 * Bridges Jackson 3 ({@code tools.jackson}) trees to Jackson 2
 * ({@code com.fasterxml.jackson}) trees.
 *
 * <p>The application runs on Jackson 3 (Spring Boot 4 default), but the networknt
 * JSON Schema validator (1.5.x line, including the project's custom
 * {@code x-core-ref} keyword) consumes Jackson 2 nodes. The 2.x/3.x line of the
 * validator removed error codes and overhauled the custom-keyword API, so staying
 * on 1.5.x with this thin bridge is the deliberate trade-off; the conversion is a
 * serialize/parse round-trip and only happens at validation boundaries.
 */
public final class JacksonBridge {

    private static final com.fasterxml.jackson.databind.ObjectMapper JACKSON2 =
        new com.fasterxml.jackson.databind.ObjectMapper();

    private JacksonBridge() {}

    /** Convert a Jackson 3 tree into the Jackson 2 tree the networknt validator consumes. */
    public static com.fasterxml.jackson.databind.JsonNode toJackson2(tools.jackson.databind.JsonNode node) {
        if (node == null) return com.fasterxml.jackson.databind.node.NullNode.getInstance();
        try {
            return JACKSON2.readTree(node.toString());
        } catch (Exception e) {
            throw new IllegalStateException("Could not bridge JSON tree to the validator", e);
        }
    }
}
