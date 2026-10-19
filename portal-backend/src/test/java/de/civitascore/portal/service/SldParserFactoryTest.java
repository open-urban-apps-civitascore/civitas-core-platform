package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Opening an address a document chose is only observable on the resolver, so these tests substitute
 * a recording one for the deny-all resolver and count what the parser asks for.
 */
class SldParserFactoryTest {

  private static final String DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

  private final SldParserFactory factory = new SldParserFactory();

  /** The addresses the parser asked to open while reading {@code document}. */
  private List<String> addressesOpenedFor(String document) {
    List<String> asked = new ArrayList<>();
    XMLInputFactory inputFactory = factory.newInputFactory();
    inputFactory.setProperty(
        XMLInputFactory.RESOLVER,
        (XMLResolver)
            (publicId, systemId, baseUri, namespace) -> {
              asked.add(systemId);
              return InputStream.nullInputStream();
            });
    try {
      XMLStreamReader reader = inputFactory.createXMLStreamReader(new StringReader(document));
      while (reader.hasNext()) {
        reader.next();
      }
    } catch (XMLStreamException e) {
      // Whether the document parses is the validator's concern; this test only counts requests.
    }
    return asked;
  }

  @Test
  @DisplayName("Should not open an external parameter entity referenced in the internal subset")
  void shouldNotOpenExternalParameterEntity() {
    String document =
        DECL
            + "<!DOCTYPE StyledLayerDescriptor"
            + " [ <!ENTITY % p SYSTEM \"file:///etc/passwd\"> %p; ]><StyledLayerDescriptor/>";

    assertThat(addressesOpenedFor(document)).isEmpty();
  }

  @Test
  @DisplayName("Should not open an external entity declared in the internal subset")
  void shouldNotOpenExternalEntity() {
    String document =
        DECL
            + "<!DOCTYPE StyledLayerDescriptor"
            + " [ <!ENTITY x SYSTEM \"file:///etc/passwd\"> ]>"
            + "<StyledLayerDescriptor>&x;</StyledLayerDescriptor>";

    assertThat(addressesOpenedFor(document)).isEmpty();
  }

  @Test
  @DisplayName("Should ask for the external subset a declaration names, so an empty result counts")
  void shouldAskForAnExternalSubset() {
    // Without this, the assertions above would hold on a parser that consults no resolver at all.
    String document =
        DECL
            + "<!DOCTYPE StyledLayerDescriptor SYSTEM \"http://127.0.0.1:9/x.dtd\">"
            + "<StyledLayerDescriptor/>";

    assertThat(addressesOpenedFor(document)).containsExactly("http://127.0.0.1:9/x.dtd");
  }

  @Test
  @DisplayName("Should carry every control the deny-all behaviour rests on")
  void shouldCarryEveryControl() {
    XMLInputFactory inputFactory = factory.newInputFactory();

    assertThat(inputFactory.getProperty(XMLInputFactory.SUPPORT_DTD)).isEqualTo(true);
    assertThat(inputFactory.getProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES))
        .isEqualTo(false);
    assertThat(inputFactory.getProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES))
        .isEqualTo(true);
    assertThat(inputFactory.getProperty(XMLConstants.ACCESS_EXTERNAL_DTD)).isEqualTo("");
    assertThat(inputFactory.getProperty(XMLInputFactory.RESOLVER)).isNotNull();
  }

  @Test
  @DisplayName("Should hand out a separate instance per call, since a factory may not be shared")
  void shouldHandOutASeparateInstancePerCall() {
    assertThat(factory.newInputFactory()).isNotSameAs(factory.newInputFactory());
  }
}
