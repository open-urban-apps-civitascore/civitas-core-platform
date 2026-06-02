package de.civitascore.portal.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.DataSinkType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DataSink Entity Tests")
class DataSinkEntityTest {

  private Pipeline pipeline(UUID id) {
    Pipeline p = new Pipeline();
    p.setId(id);
    p.setName("pipeline-" + id.toString().substring(0, 8));
    return p;
  }

  @Nested
  @DisplayName("Field assignment")
  class FieldAssignment {

    @Test
    @DisplayName("Should hold all assigned fields for a POSTGIS sink")
    void shouldHoldPostgisFields() {
      Pipeline pipeline = pipeline(UUID.randomUUID());
      Map<String, Object> config =
          Map.of(
              "tableName", "traffic_data", "dataStructureVersionId", UUID.randomUUID().toString());

      DataSink sink = new DataSink();
      sink.setPipeline(pipeline);
      sink.setDataSinkType(DataSinkType.POSTGIS);
      sink.setConfiguration(config);

      assertThat(sink.getPipeline()).isSameAs(pipeline);
      assertThat(sink.getDataSinkType()).isEqualTo(DataSinkType.POSTGIS);
      assertThat(sink.getConfiguration()).containsKey("tableName");
    }

    @Test
    @DisplayName("Should hold an empty configuration for a FROST sink")
    void shouldHoldFrostFields() {
      DataSink sink = new DataSink();
      sink.setDataSinkType(DataSinkType.FROST);
      sink.setConfiguration(Map.of());

      assertThat(sink.getDataSinkType()).isEqualTo(DataSinkType.FROST);
      assertThat(sink.getConfiguration()).isEmpty();
    }

    @Test
    @DisplayName("Configuration may be null")
    void configurationMayBeNull() {
      DataSink sink = new DataSink();
      sink.setDataSinkType(DataSinkType.FROST);
      sink.setConfiguration(null);

      assertThat(sink.getConfiguration()).isNull();
    }
  }
}
