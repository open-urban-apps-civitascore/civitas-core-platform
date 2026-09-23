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
                // xs:documentation holds mixed content, so a markup-formatted block (e.g. a nested
                // <p>) starts with a whitespace text node. Reading only item(0) would see blank and
                // drop the whole description; concatenate every node instead.
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < markup.getLength(); i++) {
                    String part = markup.item(i).getTextContent();
                    if (part != null) text.append(part);
                }
                String joined = text.toString().trim();
                return joined.isEmpty() ? null : joined;
            })
            .filter(Objects::nonNull)
            .findFirst();
    }
}
