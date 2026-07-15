package de.civitascore.modelforge.util;

import org.apache.ws.commons.schema.XmlSchemaAnnotated;
import org.apache.ws.commons.schema.XmlSchemaAnnotation;
import org.apache.ws.commons.schema.XmlSchemaDocumentation;
import org.w3c.dom.NodeList;

import java.util.Objects;
import java.util.Optional;

/**
 * Extracts {@code xs:annotation/xs:documentation} text from an annotated XSD node.
 *
 * <p>Shared by the XSD property extractor and the XSD → JSON Schema converter so the
 * (non-trivial) "first non-blank documentation block" pipeline lives in one place.
 */
public final class XsdDocumentation {

    private XsdDocumentation() {}

    /** The first non-blank {@code xs:documentation} text on {@code annotated}, if any. */
    public static Optional<String> firstText(XmlSchemaAnnotated annotated) {
        XmlSchemaAnnotation annotation = annotated.getAnnotation();
        if (annotation == null) return Optional.empty();
        return annotation.getItems().stream()
            .filter(XmlSchemaDocumentation.class::isInstance)
            .map(XmlSchemaDocumentation.class::cast)
            .map(doc -> {
                NodeList markup = doc.getMarkup();
                if (markup == null || markup.getLength() == 0) return null;
                String text = markup.item(0).getTextContent();
                return text != null && !text.isBlank() ? text.trim() : null;
            })
            .filter(Objects::nonNull)
            .findFirst();
    }
}
