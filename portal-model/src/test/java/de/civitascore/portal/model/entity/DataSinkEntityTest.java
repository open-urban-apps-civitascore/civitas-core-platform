package de.civitascore.portal.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.DataSinkType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DataSink Entity Tests")
class DataSinkEntityTest {

  private DataSet dataSet(UUID id) {
    DataSet ds = new DataSet();
    ds.setId(id);
    ds.setName("dataset-" + id.toString().substring(0, 8));
    return ds;
  }

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
      DataSet dataSet = dataSet(UUID.randomUUID());
      Pipeline pipeline = pipeline(UUID.randomUUID());

      DataSink sink = new DataSink();
      sink.setDataSet(dataSet);
      sink.setPipeline(pipeline);
      sink.setDataSinkType(DataSinkType.POSTGIS);
      sink.setConfigurationLogicalUrn("urn:core:platform:civitas:data-sink:common:traffic-data");
      sink.setConfigurationUrn("urn:core:platform:civitas:data-sink:common:traffic-data:1.0.0");

      assertThat(sink.getDataSet()).isSameAs(dataSet);
      assertThat(sink.getPipeline()).isSameAs(pipeline);
      assertThat(sink.getDataSinkType()).isEqualTo(DataSinkType.POSTGIS);
      assertThat(sink.getConfigurationLogicalUrn())
          .isEqualTo("urn:core:platform:civitas:data-sink:common:traffic-data");
      assertThat(sink.getConfigurationUrn())
          .isEqualTo("urn:core:platform:civitas:data-sink:common:traffic-data:1.0.0");
    }

    @Test
    @DisplayName("FROST sink stores no configuration: URN pins stay null, no pipeline link")
    void shouldHoldFrostFields() {
      DataSet dataSet = dataSet(UUID.randomUUID());

      DataSink sink = new DataSink();
      sink.setDataSet(dataSet);
      sink.setDataSinkType(DataSinkType.FROST);

      assertThat(sink.getDataSet()).isSameAs(dataSet);
      assertThat(sink.getPipeline()).isNull();
      assertThat(sink.getDataSinkType()).isEqualTo(DataSinkType.FROST);
      assertThat(sink.getConfigurationLogicalUrn()).isNull();
      assertThat(sink.getConfigurationUrn()).isNull();
    }
  }
}
