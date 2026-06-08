package de.civitascore.portal.model.embedded;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PermissionName Tests")
class PermissionNameTest {

  @Nested
  @DisplayName("Dashboard permissions")
  class DashboardPermissions {

    @Test
    @DisplayName("Should expose DATASET_DASHBOARD_READ and DATASET_DASHBOARD_WRITE values")
    void shouldExposeDashboardValues() {
      assertThat(PermissionName.valueOf("DATASET_DASHBOARD_READ"))
          .isEqualTo(PermissionName.DATASET_DASHBOARD_READ);
      assertThat(PermissionName.valueOf("DATASET_DASHBOARD_WRITE"))
          .isEqualTo(PermissionName.DATASET_DASHBOARD_WRITE);
    }

    @Test
    @DisplayName("Should classify DATASET_DASHBOARD_READ as a data permission")
    void shouldClassifyDashboardReadAsData() {
      PermissionName permission = PermissionName.DATASET_DASHBOARD_READ;

      assertThat(permission.getPermissionType()).isEqualTo(PermissionType.DATA);
      assertThat(permission.getCategory()).isEqualTo(PermissionCategory.DATA);
      assertThat(permission.getSource()).isEqualTo(PermissionSource.DASHBOARD);
    }

    @Test
    @DisplayName("Should classify DATASET_DASHBOARD_WRITE as a data permission")
    void shouldClassifyDashboardWriteAsData() {
      PermissionName permission = PermissionName.DATASET_DASHBOARD_WRITE;

      assertThat(permission.getPermissionType()).isEqualTo(PermissionType.DATA);
      assertThat(permission.getCategory()).isEqualTo(PermissionCategory.DATA);
      assertThat(permission.getSource()).isEqualTo(PermissionSource.DASHBOARD);
    }

    @Test
    @DisplayName("Should expose the enum constant name as its granted authority")
    void shouldExposeNameAsAuthority() {
      assertThat(PermissionName.DATASET_DASHBOARD_READ.getAuthority())
          .isEqualTo("DATASET_DASHBOARD_READ");
      assertThat(PermissionName.DATASET_DASHBOARD_WRITE.getAuthority())
          .isEqualTo("DATASET_DASHBOARD_WRITE");
    }
  }
}
