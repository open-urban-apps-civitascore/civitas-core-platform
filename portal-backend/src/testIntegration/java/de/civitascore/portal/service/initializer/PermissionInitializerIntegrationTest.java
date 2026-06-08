package de.civitascore.portal.service.initializer;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.model.embedded.PermissionCategory;
import de.civitascore.portal.model.embedded.PermissionName;
import de.civitascore.portal.model.embedded.PermissionSource;
import de.civitascore.portal.model.embedded.PermissionType;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.repository.PermissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies the dashboard permissions are seeded into the database by the real startup path. {@code
 * PermissionRoleInitializer} (an {@link org.springframework.boot.ApplicationRunner}) runs during
 * Spring context boot and delegates to {@code PermissionInitializer}; these tests assert the rows
 * exist purely from that startup, without re-seeding. {@code PortalTestDataFactory.cleanAll()} does
 * not delete permissions, so the seeded rows survive other tests' cleanup.
 */
@DisplayName("PermissionInitializer Integration Tests")
class PermissionInitializerIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private PermissionRepository permissionRepository;

  @Test
  @DisplayName(
      "Should persist DATASET_DASHBOARD_READ as a DATA permission sourced from DASHBOARD on startup")
  void shouldPersistDashboardRead() {
    Permission dashboardRead =
        permissionRepository.findByName(PermissionName.DATASET_DASHBOARD_READ.name()).orElseThrow();

    assertThat(dashboardRead.getPermissionType()).isEqualTo(PermissionType.DATA);
    assertThat(dashboardRead.getCategory()).isEqualTo(PermissionCategory.DATA);
    assertThat(dashboardRead.getSource()).isEqualTo(PermissionSource.DASHBOARD);
  }

  @Test
  @DisplayName(
      "Should persist DATASET_DASHBOARD_WRITE as a DATA permission sourced from DASHBOARD on startup")
  void shouldPersistDashboardWrite() {
    Permission dashboardWrite =
        permissionRepository
            .findByName(PermissionName.DATASET_DASHBOARD_WRITE.name())
            .orElseThrow();

    assertThat(dashboardWrite.getPermissionType()).isEqualTo(PermissionType.DATA);
    assertThat(dashboardWrite.getCategory()).isEqualTo(PermissionCategory.DATA);
    assertThat(dashboardWrite.getSource()).isEqualTo(PermissionSource.DASHBOARD);
  }
}
