package de.civitascore.portal.mapper.dcat;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.annotations.JsonLDProperty;
import de.civitascore.portal.model.annotations.JsonLDResource;
import de.civitascore.portal.model.output.BaseOutputDTO;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {GenericDcatMapper.class})
public class GenericDcatMapperTest {

  String expectedSerializedModel = """
          {
              "@graph": [
                  {
                      "@id": "4acc1107-407f-4067-8a58-ddd94f9ee7e4",
                      "http://foo.bar/name": "Nested Entity",
                      "@type": "http://foo.bar/nestedGenericDcatTestEntity"
                  },
                  {
                      "@id": "43f674a4-6c24-477b-9bcf-70729cbaff99",
                      "urn:field:GenericDcatTestEntityOutputDto#nestedGenericDcatTestEntityOutputDto": {
                          "@id": "4acc1107-407f-4067-8a58-ddd94f9ee7e4"
                      },
                      "http://foo.bar/name": "Test Entity",
                      "@type": "http://foo.bar/genericDcatTestEntity"
                  }
              ],
              "@context": {
                  "dct": "http://purl.org/dc/terms/",
                  "dcat": "http://www.w3.org/ns/dcat#",
                  "dcatde": "http://dcat-ap.de/def/dcatde/",
                  "foaf": "http://xmlns.com/foaf/0.1/"
              }
          }
          """;

  @Autowired GenericDcatMapper<BaseOutputDTO> mapper;

  @Test
  void testGenericDcatMapping() {
    NestedGenericDcatTestEntityOutputDto nestedDto =
        NestedGenericDcatTestEntityOutputDto.builder().name("Nested Entity").build();
    nestedDto.setId(UUID.fromString("4acc1107-407f-4067-8a58-ddd94f9ee7e4"));

    GenericDcatTestEntityOutputDto dto =
        GenericDcatTestEntityOutputDto.builder()
            .name("Test Entity")
            .nestedGenericDcatTestEntityOutputDto(nestedDto)
            .build();
    dto.setId(UUID.fromString("43f674a4-6c24-477b-9bcf-70729cbaff99"));

    Model resultingModel = mapper.toModel(dto);
    assertThat(resultingModel).isNotNull();
    String result = mapper.toJsonLd(resultingModel);
    assertThat(result).isEqualTo(expectedSerializedModel);
  }

  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @JsonLDResource("http://foo.bar/genericDcatTestEntity")
  static class GenericDcatTestEntityOutputDto extends BaseOutputDTO {
    @JsonLDProperty(nameSpace = "http://foo.bar/", localName = "name")
    String name;

    NestedGenericDcatTestEntityOutputDto nestedGenericDcatTestEntityOutputDto;
  }

  @Getter
  @Setter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonLDResource("http://foo.bar/nestedGenericDcatTestEntity")
  static class NestedGenericDcatTestEntityOutputDto extends BaseOutputDTO {
    @JsonLDProperty(nameSpace = "http://foo.bar/", localName = "name")
    String name;
  }
}
