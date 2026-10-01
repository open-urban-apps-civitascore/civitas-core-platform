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

      String logicalUrn =
          "urn:core:platform:civitas:datasink:common:3f2b8c1e-7a4d-4e9b-9c6f-1d2e3f4a5b6c";
      String urn = logicalUrn + ":1.0.0";

      DataSink sink = new DataSink();
      sink.setDataSet(dataSet);
      sink.setPipeline(pipeline);
      sink.setDataSinkType(DataSinkType.POSTGIS);
      sink.setConfigurationLogicalUrn(logicalUrn);
      sink.setConfigurationUrn(urn);

      assertThat(sink.getDataSet()).isSameAs(dataSet);
      assertThat(sink.getPipeline()).isSameAs(pipeline);
      assertThat(sink.getDataSinkType()).isEqualTo(DataSinkType.POSTGIS);
      assertThat(sink.getConfigurationLogicalUrn()).isEqualTo(logicalUrn);
      assertThat(sink.getConfigurationUrn()).isEqualTo(urn);
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
