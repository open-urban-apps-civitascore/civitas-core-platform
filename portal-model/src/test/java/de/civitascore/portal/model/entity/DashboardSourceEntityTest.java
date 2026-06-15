package de.civitascore.portal.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DashboardSource Entity Tests")
class DashboardSourceEntityTest {

  @Nested
  @DisplayName("Field assignment")
  class FieldAssignment {

    @Test
    @DisplayName("Should hold all fields")
    void shouldHoldAllFields() {
      UUID datasetId = UUID.randomUUID();

      DashboardSource source = new DashboardSource();
      source.setExternalSourceId("superset-ds-7");
      source.setMappedDatasetId(datasetId);
      source.setPhysicalSourceIdentifier("orders");

      assertThat(source.getExternalSourceId()).isEqualTo("superset-ds-7");
      assertThat(source.getMappedDatasetId()).isEqualTo(datasetId);
      assertThat(source.getPhysicalSourceIdentifier()).isEqualTo("orders");
    }
  }
}
