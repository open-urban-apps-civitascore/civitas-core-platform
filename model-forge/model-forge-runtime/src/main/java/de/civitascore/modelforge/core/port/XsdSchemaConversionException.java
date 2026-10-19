package de.civitascore.modelforge.core.port;

/**
 * Thrown when an XSD document cannot be converted to JSON Schema.
 */
public class XsdSchemaConversionException extends RuntimeException {

    public XsdSchemaConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
