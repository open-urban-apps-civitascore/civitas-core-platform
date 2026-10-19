package de.civitascore.modelforge.util;

/**
 * Single source of truth for classifying XSD built-in types into the JSON type vocabulary.
 *
 * <p>The XSD built-in classification (which local names are integers, numbers, dates, …)
 * is consumed by {@code XsdToJsonSchemaConverter} to render {@code {type, format}} pairs
 * from the canonical {@link Kind}.
 */
public final class XsdTypeMapping {

    private XsdTypeMapping() {}

    /** Canonical category of an XSD built-in type, independent of output representation. */
    public enum Kind { STRING, INTEGER, NUMBER, BOOLEAN, DATE, DATE_TIME, TIME, BINARY }

    /**
     * Classify an XSD built-in type by its local name (e.g. {@code "dateTime"}).
     * Unknown / textual names fall back to {@link Kind#STRING}.
     */
    public static Kind kindOf(String xsdLocalName) {
        if (xsdLocalName == null) return Kind.STRING;
        return switch (xsdLocalName) {
            case "integer", "int", "long", "short", "byte",
                 "nonNegativeInteger", "positiveInteger", "nonPositiveInteger",
                 "negativeInteger", "unsignedInt", "unsignedLong", "unsignedShort",
                 "unsignedByte" -> Kind.INTEGER;
            case "decimal", "float", "double"  -> Kind.NUMBER;
            case "boolean"                     -> Kind.BOOLEAN;
            case "date"                        -> Kind.DATE;
            case "dateTime"                    -> Kind.DATE_TIME;
            case "time"                        -> Kind.TIME;
            case "base64Binary", "hexBinary"   -> Kind.BINARY;
            default                            -> Kind.STRING;
        };
    }
}
