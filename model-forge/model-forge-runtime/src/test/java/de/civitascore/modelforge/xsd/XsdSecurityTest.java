package de.civitascore.modelforge.xsd;

import tools.jackson.databind.ObjectMapper;
import de.civitascore.modelforge.xsd.XsdToJsonSchemaConverter;
import de.civitascore.modelforge.util.SecureXsdParser;
import org.apache.ws.commons.schema.XmlSchema;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XsdSecurityTest {

    private static final String XSD_WITH_DOCTYPE = """
        <!DOCTYPE foo [
          <!ENTITY xxe SYSTEM "file:///etc/passwd">
        ]>
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <xs:complexType name="Unsafe"/>
        </xs:schema>
        """;

    @Test
    void jsonSchemaConversionRejectsDoctypeAndExternalEntities() {
        XsdToJsonSchemaConverter converter = new XsdToJsonSchemaConverter(new ObjectMapper());

        assertThatThrownBy(() -> converter.convert(XSD_WITH_DOCTYPE, "urn:core:test:owner:element:domain"))
            .isInstanceOf(de.civitascore.modelforge.core.port.XsdSchemaConversionException.class)
            .hasMessageContaining("Unsafe XML");
    }

    @Test
    void importInclude_externalSchemaLocationIsNeitherFetchedNorRead() throws Exception {
        // A real local file that is NOT a schema. Without the deny-all schema resolver,
        // WS-Commons would open and try to parse this schemaLocation (SSRF / local-file read)
        // and fail; with the resolver installed it is never touched and the parse succeeds.
        Path bait = Files.createTempFile("xxe-bait", ".txt");
        bait.toFile().deleteOnExit();
        Files.writeString(bait, "this is not xml <<< {} definitely not a schema");

        String xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:demo">
              <xs:include schemaLocation="%s"/>
              <xs:element name="root" type="xs:string"/>
            </xs:schema>
            """.formatted(bait.toUri());

        XmlSchema schema = SecureXsdParser.parse(xsd);
        assertThat(schema).isNotNull();
    }
}
