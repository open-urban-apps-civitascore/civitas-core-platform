package de.civitascore.portal.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Dashboard Entity Tests")
class DashboardEntityTest {

  @Nested
  @DisplayName("Field assignment")
  class FieldAssignment {

    @Test
    @DisplayName("Holds the dashboard-specific fields, the dataset reference, and bound sources")
    void shouldHoldAllFields() {
      DataSet dataset = new DataSet();
      dataset.setId(UUID.randomUUID());
      DashboardSource source = new DashboardSource();
      source.setId(UUID.randomUUID());

      Dashboard dashboard = new Dashboard();
      dashboard.setExternalDashboardId("dash-42");
      dashboard.setActive(false);
      dashboard.setDataset(dataset);
      dashboard.getSources().add(source);

      assertThat(dashboard.getExternalDashboardId()).isEqualTo("dash-42");
      assertThat(dashboard.isActive()).isFalse();
      assertThat(dashboard.getDataset()).isSameAs(dataset);
      assertThat(dashboard.getSources()).containsExactly(source);
    }
  }

  @Nested
  @DisplayName("Defaults")
  class Defaults {

    @Test
    @DisplayName("active should default to true")
    void activeDefaultsToTrue() {
      assertThat(new Dashboard().isActive()).isTrue();
    }

    @Test
    @DisplayName("sources should default to an empty set")
    void sourcesDefaultsToEmptySet() {
      assertThat(new Dashboard().getSources()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("externalDashboardId is null until the engine confirms it")
    void externalDashboardIdDefaultsToNull() {
      assertThat(new Dashboard().getExternalDashboardId()).isNull();
      assertThat(Dashboard.builder().name("D").build().getExternalDashboardId()).isNull();
    }
  }
}
