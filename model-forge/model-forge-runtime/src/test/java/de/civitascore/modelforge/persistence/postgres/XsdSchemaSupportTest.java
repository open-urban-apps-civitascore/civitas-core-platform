package de.civitascore.modelforge.persistence.postgres;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.xsd.XsdToJsonSchemaConverter;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class XsdSchemaSupportTest {

    private static final String XSD_URN =
        "urn:core:platform:civitas:element:xmeld:0b6f5c1e-8a4d-4f7b-9c2e-3d1a5b7c9e20:1.0.0";

    private final ObjectMapper mapper = new ObjectMapper();
    private final XsdSchemaSupport support =
        new XsdSchemaSupport(new XsdToJsonSchemaConverter(mapper), mapper);

    /** Type15's derived UUID starts with eight digits, so a bare-URN prefix makes it read as an XSD version. */
    @Test
    void aConvertedTypeIdIsNeverACoreUrn() {
        String xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="Type15">
                <xs:sequence><xs:element name="name" type="xs:string"/></xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

        JsonNode schema = support.toJsonSchema(XSD_URN, null, () -> Optional.of(xsd)).orElseThrow();

        assertThat(UrnParser.isUrn(schema.path("$id").asText())).isFalse();
    }
}
