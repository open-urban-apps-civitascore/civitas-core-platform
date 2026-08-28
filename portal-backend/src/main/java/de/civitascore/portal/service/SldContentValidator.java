package de.civitascore.portal.service;

import de.civitascore.portal.util.InvalidInputException;
import java.io.InputStream;
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Component;

/**
 * Rejects SLD documents GeoServer cannot parse: one that declares a DTD, and one that is not
 * well-formed XML. GeoServer refuses both with an opaque server error while publishing, so checking
 * here turns a late saga failure into a field-level error at submission.
 */
@Component
@Slf4j
public class SldContentValidator {

  /** Field name reported to the client so it can highlight the offending input. */
  private static final String FIELD = "sldContent";

  private static final String RESOURCE_TYPE = "Style";

  /**
   * Verifies that GeoServer would accept this SLD document. Blank content is left to {@code
   * NotBlank} on the input DTO.
   *
   * @throws InvalidInputException if the document declares a DTD or is not well-formed XML
   */
  public void validate(String sldContent) {
    if (StringUtils.isBlank(sldContent)) {
      return;
    }

    XMLStreamReader reader = null;
    try {
      reader = secureFactory().createXMLStreamReader(new StringReader(sldContent));
      while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.DTD) {
          throw doctypeDeclared();
        }
      }
    } catch (XMLStreamException e) {
      // The 400 handler already logs the position; this adds the parser's own wording, which the
      // client message drops as locale-dependent.
      log.warn("Rejected SLD upload as not well-formed: {}", Encode.forJava(e.getMessage()));
      throw notWellFormed(e);
    } finally {
      closeQuietly(reader);
    }
  }

  /**
   * A parser that reports a DTD as an event so a declared one is named in the error, while opening
   * nothing a document points at: an empty external subset stands in for whatever the declaration
   * references. A document declaring a DTD is refused before anything in it is expanded, so
   * resolving entity references only ever applies to the five XML defines itself — which is what
   * makes an undeclared reference a fault the user hears about here rather than from GeoServer
   * later.
   *
   * <p>Refusing external entities is not redundant: without it an external parameter entity
   * referenced in an internal subset reaches the resolver before the DTD event fires.
   */
  private static XMLInputFactory secureFactory() {
    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, true);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true);
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setProperty(
        XMLInputFactory.RESOLVER,
        (XMLResolver) (publicId, systemId, baseUri, namespace) -> InputStream.nullInputStream());
    return factory;
  }

  private static InvalidInputException doctypeDeclared() {
    return new InvalidInputException(
        RESOURCE_TYPE,
        FIELD,
        "Style "
            + FIELD
            + " must not declare a DOCTYPE. GeoServer refuses any document containing a"
            + " <!DOCTYPE ...> declaration — remove that line and upload the style again.");
  }

  private static InvalidInputException notWellFormed(XMLStreamException cause) {
    int line = cause.getLocation() != null ? cause.getLocation().getLineNumber() : -1;
    int column = cause.getLocation() != null ? cause.getLocation().getColumnNumber() : -1;
    // The parser's own wording is deliberately not echoed: the JDK localises it to the server's
    // default locale, which would make a client-facing message depend on where the backend runs.
    return new InvalidInputException(
        RESOURCE_TYPE,
        FIELD,
        "Style "
            + FIELD
            + " is not well-formed XML at line "
            + line
            + ", column "
            + column
            + ". Fix the XML at that position and upload the style again.");
  }

  private static void closeQuietly(XMLStreamReader reader) {
    if (reader == null) {
      return;
    }
    try {
      reader.close();
    } catch (XMLStreamException e) {
      // Nothing actionable: the document has already been accepted or rejected.
    }
  }
}
