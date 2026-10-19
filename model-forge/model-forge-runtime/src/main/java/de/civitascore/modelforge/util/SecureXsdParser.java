package de.civitascore.modelforge.util;

import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.resolver.URIResolver;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

/**
 * Parses XSD content with three layers of XXE / SSRF protection:
 *
 * <ol>
 *   <li>{@link XmlInputGuard} — quick string-scan rejects DOCTYPE / ENTITY declarations
 *       before any parser is invoked.
 *   <li>A {@link DocumentBuilderFactory} hardened with {@code FEATURE_SECURE_PROCESSING},
 *       {@code disallow-doctype-decl}, and disabled external-entity features — blocks any
 *       encoding-variant payload that slips past the string check.
 *   <li>A deny-all {@link URIResolver} on the {@link XmlSchemaCollection} — {@code xs:import}
 *       and {@code xs:include} are resolved to an empty in-memory schema instead of fetching
 *       their declared {@code schemaLocation}. The DOM hardening above does <em>not</em> cover
 *       WS-Commons' own schema-import resolution, so without this an absolute
 *       {@code http(s)://}/{@code file://} location would be opened at parse time
 *       (server-side request forgery and local-file disclosure).
 * </ol>
 *
 * <p>{@link XmlSchemaCollection} reads the pre-parsed {@link Document} so there is no
 * second parse round, and the hardened configuration applies to the full content.
 */
public final class SecureXsdParser {

    private SecureXsdParser() {}

    /**
     * Parse {@code xsdContent} into an {@link XmlSchema} model.
     *
     * @throws IllegalArgumentException if the content contains unsafe XML constructs
     * @throws Exception                if the content is not well-formed XML
     */
    public static XmlSchema parse(String xsdContent) throws Exception {
        XmlInputGuard.rejectUnsafeXml(xsdContent);
        Document doc = buildSecureDocumentBuilder()
                .parse(new InputSource(new StringReader(xsdContent)));
        XmlSchemaCollection collection = new XmlSchemaCollection();
        // Never open an external/local schemaLocation referenced by xs:import / xs:include.
        collection.setSchemaResolver(DENY_EXTERNAL_RESOLVER);
        return collection.read(doc, null);
    }

    /**
     * Resolves every {@code xs:import}/{@code xs:include} to an empty in-memory schema, so no
     * declared {@code schemaLocation} is ever fetched or read from disk. The imported namespace
     * is echoed into the stub's {@code targetNamespace} so a legitimate import resolves cleanly
     * (to an empty type set) rather than failing on a namespace mismatch.
     */
    private static final URIResolver DENY_EXTERNAL_RESOLVER =
            (namespace, schemaLocation, baseUri) -> new InputSource(new StringReader(emptySchema(namespace)));

    private static String emptySchema(String namespace) {
        String tns = (namespace == null || namespace.isBlank())
                ? ""
                : " targetNamespace=\"" + xmlAttr(namespace) + "\"";
        return "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"" + tns + "/>";
    }

    private static String xmlAttr(String value) {
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
    }

    private static DocumentBuilder buildSecureDocumentBuilder() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        // Parser-level XXE hardening — defence-in-depth beyond the string pre-check
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,                       true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl",       true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities",      false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities",    false);
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,    "");
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return dbf.newDocumentBuilder();
    }
}
