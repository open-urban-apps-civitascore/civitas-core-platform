package de.civitascore.portal.model.embedded;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RoleDefault Tests")
class RoleDefaultTest {

  @Nested
  @DisplayName("getPermissions()")
  class GetPermissionsTests {

    @Test
    @DisplayName("Should return unmodifiable list")
    void shouldReturnUnmodifiableList() {
      List<PermissionName> permissions = RoleDefault.TENANT_ADMIN.getPermissions();

      assertThatThrownBy(() -> permissions.add(PermissionName.GROUP_READ))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should not allow removal from list")
    void shouldNotAllowRemoval() {
      List<PermissionName> permissions = RoleDefault.TENANT_ADMIN.getPermissions();

      assertThatThrownBy(() -> permissions.remove(0))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should not allow clearing the list")
    void shouldNotAllowClear() {
      List<PermissionName> permissions = RoleDefault.TENANT_ADMIN.getPermissions();

      assertThatThrownBy(() -> permissions.clear())
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Each role default should have at least one permission")
    void eachRoleShouldHavePermissions() {
      for (RoleDefault role : RoleDefault.values()) {
        assertThat(role.getPermissions())
            .as("RoleDefault.%s should have at least one permission", role.name())
            .isNotEmpty();
      }
    }

    @Test
    @DisplayName("TENANT_ADMIN should have all CRUD permissions")
    void tenantAdminShouldHaveAllCrudPermissions() {
      List<PermissionName> permissions = RoleDefault.TENANT_ADMIN.getPermissions();

      assertThat(permissions)
          .contains(
              PermissionName.GROUP_READ,
              PermissionName.GROUP_CREATE,
              PermissionName.ROLE_READ,
              PermissionName.ROLE_CREATE,
              PermissionName.USER_READ,
              PermissionName.USER_CREATE);
    }
  }
}
