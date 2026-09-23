package de.civitascore.modelforge.xsd;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import de.civitascore.modelforge.core.port.XsdSchemaConversionException;
import de.civitascore.modelforge.testsupport.Fixtures;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class XsdToJsonSchemaConverterTest {

    private static final String URN_PREFIX = "urn:core:standard:xoev:element:xadvanced";

    /** Expected converter URN: prefix + name + derived disambiguator (logical, version-less). */
    private static String urn(String name) {
        return URN_PREFIX + ":" + name + ":"
            + de.civitascore.modelforge.urn.UrnParser.deriveDisambiguator(name);
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final XsdToJsonSchemaConverter converter = new XsdToJsonSchemaConverter(mapper);

    @Test
    void convertsComplexSimpleExtensionChoiceAllAndFacetMappings() {
        Map<String, ObjectNode> schemas = converter.convert(
            Fixtures.text("xsd/advanced-types.xsd"),
            URN_PREFIX
        );

        assertThat(schemas).containsKeys(
            "BoundedCode",
            "Percentage",
            "KeywordList",
            "BaseThing",
            "ExtendedThing",
            "MeasurementValue",
            "ChoiceContainer",
            "AllContainer",
            "InlinePayload"
        );

        ObjectNode boundedCode = schemas.get("BoundedCode");
        assertThat(boundedCode.path("$schema").asText()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
        assertThat(boundedCode.path("$id").asText()).isEqualTo(urn("BoundedCode"));
        assertThat(boundedCode.path("description").asText()).isEqualTo("Short uppercase code.");
        assertThat(boundedCode.path("type").asText()).isEqualTo("string");
        assertThat(boundedCode.path("minLength").asInt()).isEqualTo(2);
        assertThat(boundedCode.path("maxLength").asInt()).isEqualTo(8);
        assertThat(boundedCode.path("pattern").asText()).isEqualTo("[A-Z]+");

        ObjectNode percentage = schemas.get("Percentage");
        assertThat(percentage.path("type").asText()).isEqualTo("number");
        assertThat(percentage.path("minimum").asDouble()).isZero();
        assertThat(percentage.path("exclusiveMaximum").asDouble()).isEqualTo(100.0);
        assertThat(percentage.path("multipleOf").asDouble()).isCloseTo(0.01, within(0.000001));

        ObjectNode keywordList = schemas.get("KeywordList");
        assertThat(keywordList.path("type").asText()).isEqualTo("string");

        ObjectNode baseThing = schemas.get("BaseThing");
        assertThat(baseThing.path("properties").path("Name").path("type").asText()).isEqualTo("string");
        assertThat(baseThing.path("properties").path("Code").path("$ref").asText())
            .isEqualTo(urn("BoundedCode"));
        assertThat(baseThing.path("properties").path("@baseId").path("type").asText()).isEqualTo("string");
        List<String> required = new ArrayList<>();
        baseThing.path("required").forEach(n -> required.add(n.asText()));
        assertThat(required).containsExactly("Name", "@baseId");

        ObjectNode extendedThing = schemas.get("ExtendedThing");
        assertThat(extendedThing.path("allOf").get(0).path("$ref").asText())
            .isEqualTo(urn("BaseThing"));
        assertThat(extendedThing.path("allOf").get(1).path("properties").path("Score").path("$ref").asText())
            .isEqualTo(urn("Percentage"));

        ObjectNode measurementValue = schemas.get("MeasurementValue");
        assertThat(measurementValue.path("properties").path("_value").path("type").asText()).isEqualTo("number");
        assertThat(measurementValue.path("properties").path("@unit").path("type").asText()).isEqualTo("string");

        ObjectNode choiceContainer = schemas.get("ChoiceContainer");
        assertThat(choiceContainer.path("properties").path("Email").path("type").asText()).isEqualTo("string");
        assertThat(choiceContainer.path("properties").path("Phone").path("type").asText()).isEqualTo("string");

        ObjectNode allContainer = schemas.get("AllContainer");
        assertThat(allContainer.path("properties").path("Latitude").path("type").asText()).isEqualTo("number");
        assertThat(allContainer.path("properties").path("Longitude").path("type").asText()).isEqualTo("number");

        ObjectNode inlinePayload = schemas.get("InlinePayload");
        assertThat(inlinePayload.path("properties").path("AnyPayload").path("type").asText()).isEqualTo("object");
    }

    @Test
    void returnsEmptyMapForBlankInput() {
        assertThat(converter.convert("", URN_PREFIX)).isEmpty();
        assertThat(converter.convert(null, URN_PREFIX)).isEmpty();
    }

    @Test
    void throwsTypedExceptionForUnsafeInput() {
        // A broken/unsafe XSD must be distinguishable from one without named types
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> converter.convert("""
            <!DOCTYPE schema [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
            """, URN_PREFIX))
            .isInstanceOf(XsdSchemaConversionException.class)
            .hasMessageContaining("Unsafe XML");
    }

    @Test
    void convertsXsdDateAndDateTimeAndTimeToJsonSchemaFormats() {
        Map<String, ObjectNode> schemas = converter.convert("""
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="Event">
                <xs:sequence>
                  <xs:element name="eventDate"     type="xs:date"/>
                  <xs:element name="startDateTime" type="xs:dateTime"/>
                  <xs:element name="startTime"     type="xs:time"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """, URN_PREFIX);

        ObjectNode event = schemas.get("Event");
        assertThat(event.at("/properties/eventDate/type").asText()).isEqualTo("string");
        assertThat(event.at("/properties/eventDate/format").asText()).isEqualTo("date");
        assertThat(event.at("/properties/startDateTime/format").asText()).isEqualTo("date-time");
        assertThat(event.at("/properties/startTime/format").asText()).isEqualTo("time");
    }

    @Test
    void convertsXsdBooleanAndIntegerTypesToJsonSchemaEquivalents() {
        Map<String, ObjectNode> schemas = converter.convert("""
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="Flags">
                <xs:sequence>
                  <xs:element name="active"    type="xs:boolean"/>
                  <xs:element name="count"     type="xs:integer"/>
                  <xs:element name="amount"    type="xs:decimal"/>
                  <xs:element name="ratio"     type="xs:double"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """, URN_PREFIX);

        ObjectNode flags = schemas.get("Flags");
        assertThat(flags.at("/properties/active/type").asText()).isEqualTo("boolean");
        assertThat(flags.at("/properties/count/type").asText()).isEqualTo("integer");
        assertThat(flags.at("/properties/amount/type").asText()).isEqualTo("number");
        assertThat(flags.at("/properties/ratio/type").asText()).isEqualTo("number");
    }

    @Test
    void convertsElementWithNoTypeToInlineObject() {
        Map<String, ObjectNode> schemas = converter.convert("""
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="Wrapper">
                <xs:sequence>
                  <xs:element name="payload"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """, URN_PREFIX);

        assertThat(schemas.get("Wrapper").at("/properties/payload/type").asText()).isEqualTo("object");
    }

    @Test
    void convertsRepeatingElementToArraySchema() {
        Map<String, ObjectNode> schemas = converter.convert("""
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="List">
                <xs:sequence>
                  <xs:element name="item" type="xs:string" maxOccurs="unbounded"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """, URN_PREFIX);

        ObjectNode list = schemas.get("List");
        assertThat(list.at("/properties/item/type").asText()).isEqualTo("array");
        assertThat(list.at("/properties/item/items/type").asText()).isEqualTo("string");
    }

    @Test
    void convertsSimpleTypeWithEnumerationFacets() {
        Map<String, ObjectNode> schemas = converter.convert("""
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="StatusCode">
                <xs:restriction base="xs:string">
                  <xs:enumeration value="ACTIVE"/>
                  <xs:enumeration value="INACTIVE"/>
                  <xs:enumeration value="PENDING"/>
                </xs:restriction>
              </xs:simpleType>
            </xs:schema>
            """, URN_PREFIX);

        ObjectNode statusCode = schemas.get("StatusCode");
        assertThat(statusCode.has("enum")).isTrue();
        List<String> values = new ArrayList<>();
        statusCode.path("enum").forEach(n -> values.add(n.asText()));
        assertThat(values).containsExactlyInAnyOrder("ACTIVE", "INACTIVE", "PENDING");
    }

    @Test
    void convertsSimpleTypeWithNoRestrictionToStringFallback() {
        Map<String, ObjectNode> schemas = converter.convert("""
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="OpenText">
                <xs:union memberTypes="xs:string xs:integer"/>
              </xs:simpleType>
            </xs:schema>
            """, URN_PREFIX);

        ObjectNode openText = schemas.get("OpenText");
        assertThat(openText.path("type").asText()).isEqualTo("string");
    }

    @Test
    void convertsElementAnnotationDocumentationToDescription() {
        Map<String, ObjectNode> schemas = converter.convert("""
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="Station">
                <xs:annotation>
                  <xs:documentation>Air quality monitoring station.</xs:documentation>
                </xs:annotation>
                <xs:sequence>
                  <xs:element name="id" type="xs:string">
                    <xs:annotation>
                      <xs:documentation>Unique station identifier.</xs:documentation>
                    </xs:annotation>
                  </xs:element>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """, URN_PREFIX);

        ObjectNode station = schemas.get("Station");
        assertThat(station.path("description").asText()).isEqualTo("Air quality monitoring station.");
        assertThat(station.at("/properties/id/description").asText()).isEqualTo("Unique station identifier.");
    }

    @Test
    void convertsMinExclusiveAndMaxExclusiveFacets() {
        Map<String, ObjectNode> schemas = converter.convert("""
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="Temperature">
                <xs:restriction base="xs:decimal">
                  <xs:minExclusive value="-273.15"/>
                  <xs:maxExclusive value="1000.0"/>
                </xs:restriction>
              </xs:simpleType>
            </xs:schema>
            """, URN_PREFIX);

        ObjectNode temperature = schemas.get("Temperature");
        assertThat(temperature.path("exclusiveMinimum").asDouble()).isEqualTo(-273.15);
        assertThat(temperature.path("exclusiveMaximum").asDouble()).isEqualTo(1000.0);
    }

    @Test
    void convertsRequiredAttributeToRequiredArray() {
        Map<String, ObjectNode> schemas = converter.convert("""
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="Tagged">
                <xs:sequence/>
                <xs:attribute name="version" type="xs:string" use="required"/>
                <xs:attribute name="optional" type="xs:string"/>
              </xs:complexType>
            </xs:schema>
            """, URN_PREFIX);

        ObjectNode tagged = schemas.get("Tagged");
        List<String> required = new ArrayList<>();
        tagged.path("required").forEach(n -> required.add(n.asText()));
        assertThat(required).containsExactly("@version");
        assertThat(required).doesNotContain("@optional");
    }
}
