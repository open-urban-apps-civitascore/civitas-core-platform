package de.civitascore.modelforge.core.port;

import tools.jackson.databind.node.ObjectNode;
import java.util.Map;

/**
 * Converts XSD content into JSON Schema documents without exposing parser implementation details.
 */
public interface XsdSchemaConverter {

    Map<String, ObjectNode> convert(String xsdContent, String urnPrefix);
}
