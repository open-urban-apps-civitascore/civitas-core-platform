package de.civitascore.portal.service;

import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import org.springframework.stereotype.Component;

/**
 * Builds the parser an uploaded style is read with: it reports a DTD as an event so a declared one
 * can be named in the error, and opens nothing the document points at.
 *
 * <p>Refusing external entities is not redundant beside the other two: without it an external
 * parameter entity referenced in an internal subset reaches the resolver before the DTD event.
 */
@Component
public class SldParserFactory {

  /** A fresh instance per call — {@link XMLInputFactory} carries no thread-safety guarantee. */
  public XMLInputFactory newInputFactory() {
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
}
