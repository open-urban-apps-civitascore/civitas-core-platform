package de.civitascore.portal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.entity.Dashboard;
import de.civitascore.portal.model.input.DashboardInputDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DashboardMapper Tests")
class DashboardMapperTest {

  private final DashboardMapper mapper = new DashboardMapperImpl();

  @Test
  @DisplayName("toEntity leaves engine-owned fields unset (they are not client-writable)")
  void toEntityIgnoresEngineOwnedFields() {
    DashboardInputDTO input = new DashboardInputDTO();
    input.setName("Traffic");

    Dashboard entity = mapper.toEntity(input);

    assertThat(entity.getName()).isEqualTo("Traffic");
    // externalDashboardId and the dataset binding are populated by the engine-sync flow and the
    // service respectively — the mapper must never carry a client-supplied value into them.
    assertThat(entity.getExternalDashboardId()).isNull();
    assertThat(entity.getDataSet()).isNull();
  }

  @Test
  @DisplayName("updateEntity does not touch externalDashboardId or active")
  void updateEntityPreservesEngineOwnedFields() {
    Dashboard entity = new Dashboard();
    entity.setExternalDashboardId("engine-set-id");
    entity.setActive(false);

    DashboardInputDTO input = new DashboardInputDTO();
    input.setName("Renamed");

    mapper.updateEntity(entity, input);

    assertThat(entity.getName()).isEqualTo("Renamed");
    assertThat(entity.getExternalDashboardId()).isEqualTo("engine-set-id");
    assertThat(entity.isActive()).isFalse();
  }
}
