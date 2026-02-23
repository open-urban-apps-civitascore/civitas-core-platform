package de.civitascore.portal.service.initializer;

import static org.mockito.Mockito.inOrder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Permission Role Initializer Tests")
class PermissionRoleInitializerTest {

  @Mock private PermissionInitializer permissionInitializer;
  @Mock private RoleInitializer roleInitializer;

  @InjectMocks private PermissionRoleInitializer permissionRoleInitializer;

  @Test
  @DisplayName("Should initialize permissions before roles")
  void shouldInitializePermissionsBeforeRoles() {
    // when
    permissionRoleInitializer.run(null);

    // then
    InOrder inOrder = inOrder(permissionInitializer, roleInitializer);
    inOrder.verify(permissionInitializer).initialize();
    inOrder.verify(roleInitializer).initialize();
  }
}
