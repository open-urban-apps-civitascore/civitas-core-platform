package de.civitascore.portal.model.output;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.VisualizationType;
import de.civitascore.portal.model.input.DashboardInputDTO;
import de.civitascore.portal.model.input.VisualizationInputDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class VisualizationDTOSerializationTest {

  // Mirror the wire serializer: production controllers use Jackson 3 (tools.jackson), so the
  // discriminator round-trip must be exercised against the same generation, not Jackson 2.
  private final ObjectMapper mapper = new JsonMapper();

  @Test
  void serializesOutputDiscriminator() throws Exception {
    DashboardOutputDTO dto = new DashboardOutputDTO();
    dto.setName("Traffic");
    String json = mapper.writeValueAsString((VisualizationOutputDTO) dto);
    assertThat(json).contains("\"visualizationType\":\"DASHBOARD\"");
  }

  @Test
  void deserializesInputByDiscriminator() throws Exception {
    String json = "{\"visualizationType\":\"DASHBOARD\",\"name\":\"Traffic\"}";
    VisualizationInputDTO dto = mapper.readValue(json, VisualizationInputDTO.class);
    assertThat(dto).isInstanceOf(DashboardInputDTO.class);
    assertThat(dto.getVisualizationType()).isEqualTo(VisualizationType.DASHBOARD);
    assertThat(((DashboardInputDTO) dto).getName()).isEqualTo("Traffic");
  }

  @ParameterizedTest
  @EnumSource(VisualizationType.class)
  void everyVisualizationTypeResolvesToARegisteredSubtype(VisualizationType type) {
    // Exhaustiveness only flows subtype -> enum at compile time; this guards the reverse, so adding
    // a new VisualizationType without registering its @JsonSubTypes member fails loudly here.
    String json = "{\"visualizationType\":\"" + type.name() + "\",\"name\":\"x\"}";
    VisualizationInputDTO dto = mapper.readValue(json, VisualizationInputDTO.class);
    assertThat(dto.getVisualizationType()).isEqualTo(type);
  }
}
