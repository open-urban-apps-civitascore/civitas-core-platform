package de.civitascore.portal.model.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayName("DataSetInputDTO datapool-id presence tracking")
class DataSetInputDTOTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  @DisplayName("omitted datapoolId leaves the present-flag false")
  void omittedLeavesFlagFalse() {
    DataSetInputDTO dto = mapper.readValue("{\"name\":\"x\"}", DataSetInputDTO.class);
    assertThat(dto.getDatapoolId()).isNull();
    assertThat(dto.isDatapoolIdPresent()).isFalse();
  }

  @Test
  @DisplayName("explicit null datapoolId sets the present-flag")
  void explicitNullSetsFlag() {
    DataSetInputDTO dto =
        mapper.readValue("{\"name\":\"x\",\"datapoolId\":null}", DataSetInputDTO.class);
    assertThat(dto.getDatapoolId()).isNull();
    assertThat(dto.isDatapoolIdPresent()).isTrue();
  }

  @Test
  @DisplayName("a real datapoolId is still deserialized and sets the flag")
  void valueSetsBoth() {
    UUID id = UUID.randomUUID();
    DataSetInputDTO dto =
        mapper.readValue("{\"name\":\"x\",\"datapoolId\":\"" + id + "\"}", DataSetInputDTO.class);
    assertThat(dto.getDatapoolId()).isEqualTo(id);
    assertThat(dto.isDatapoolIdPresent()).isTrue();
  }

  @Test
  @DisplayName("a client cannot set the present-flag from the wire")
  void clientCannotForgeFlag() {
    DataSetInputDTO dto =
        mapper.readValue("{\"name\":\"x\",\"datapoolIdPresent\":true}", DataSetInputDTO.class);
    assertThat(dto.isDatapoolIdPresent()).isFalse();
  }
}
