package de.civitascore.portal.service.initializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.PermissionCategory;
import de.civitascore.portal.model.embedded.PermissionName;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.repository.PermissionRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.service.initializer.RoleInitializer.StandardRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Role Initializer Tests")
class RoleInitializerTest {

  @Mock private RoleRepository roleRepository;
  @Mock private PermissionRepository permissionRepository;

  @InjectMocks private RoleInitializer roleInitializer;

  @Captor private ArgumentCaptor<List<Role>> rolesCaptor;

  private List<Permission> allPermissions;

  @BeforeEach
  void setUp() {
    allPermissions = new ArrayList<>();
    for (PermissionName name : PermissionName.values()) {
      Permission permission = new Permission();
      permission.setId(UUID.randomUUID());
      permission.setName(name.name());
      permission.setPermissionType(name.getPermissionType());
      permission.setCategory(name.getCategory());
      permission.setSource(name.getSource());
      allPermissions.add(permission);
    }
  }

  @Test
  @DisplayName("Should create all standard roles when database is empty")
  void shouldCreateAllRolesWhenDatabaseIsEmpty() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    when(roleRepository.findAll()).thenReturn(List.of());

    // when
    roleInitializer.run(null);

    // then
    verify(roleRepository).saveAll(rolesCaptor.capture());
    List<Role> created = rolesCaptor.getValue();
    assertThat(created).hasSize(StandardRole.values().length);
  }

  @Test
  @DisplayName("Should not create duplicates when all roles already exist")
  void shouldNotCreateDuplicatesWhenAllRolesExist() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    List<Role> existingRoles = new ArrayList<>();
    for (StandardRole std : StandardRole.values()) {
      Role role = new Role();
      role.setName(std.roleName);
      existingRoles.add(role);
    }
    when(roleRepository.findAll()).thenReturn(existingRoles);

    // when
    roleInitializer.run(null);

    // then
    verify(roleRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("Should only add missing roles when some already exist")
  void shouldOnlyAddMissingRoles() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    Role existingRole = new Role();
    existingRole.setName("Platform Admin");
    when(roleRepository.findAll()).thenReturn(List.of(existingRole));

    // when
    roleInitializer.run(null);

    // then
    verify(roleRepository).saveAll(rolesCaptor.capture());
    List<Role> created = rolesCaptor.getValue();
    assertThat(created).hasSize(StandardRole.values().length - 1);
    assertThat(created).noneMatch(r -> r.getName().equals("Platform Admin"));
  }

  @Test
  @DisplayName("Should mark all created roles as readonly")
  void shouldMarkAllRolesAsReadonly() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    when(roleRepository.findAll()).thenReturn(List.of());

    // when
    roleInitializer.run(null);

    // then
    verify(roleRepository).saveAll(rolesCaptor.capture());
    List<Role> created = rolesCaptor.getValue();
    assertThat(created).allMatch(Role::isReadonly);
  }

  @Test
  @DisplayName("Should assign correct role types")
  void shouldAssignCorrectRoleTypes() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    when(roleRepository.findAll()).thenReturn(List.of());

    // when
    roleInitializer.run(null);

    // then
    verify(roleRepository).saveAll(rolesCaptor.capture());
    List<Role> created = rolesCaptor.getValue();

    assertThat(findRole(created, "Platform Admin").getRoleType()).isEqualTo(RoleType.SYSTEM);
    assertThat(findRole(created, "Tenant Admin").getRoleType()).isEqualTo(RoleType.SYSTEM);
    assertThat(findRole(created, "Data Architect").getRoleType()).isEqualTo(RoleType.DATA);
    assertThat(findRole(created, "Data Consumer").getRoleType()).isEqualTo(RoleType.DATA);
    assertThat(findRole(created, "Data Steward").getRoleType()).isEqualTo(RoleType.DATA);
    assertThat(findRole(created, "Data Owner").getRoleType()).isEqualTo(RoleType.DATA);
    assertThat(findRole(created, "Data Gatekeeper").getRoleType()).isEqualTo(RoleType.GOVERNANCE);
  }

  @Test
  @DisplayName("Should assign all tenant administration permissions to Platform Admin")
  void shouldAssignAllTenantAdminPermissionsToPlatformAdmin() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    when(roleRepository.findAll()).thenReturn(List.of());

    // when
    roleInitializer.run(null);

    // then
    verify(roleRepository).saveAll(rolesCaptor.capture());
    Role platformAdmin = findRole(rolesCaptor.getValue(), "Platform Admin");

    List<String> expectedPermissions =
        allPermissions.stream()
            .filter(p -> p.getCategory() == PermissionCategory.TENANT_ADMINISTRATION)
            .map(Permission::getName)
            .toList();

    List<String> actualPermissions =
        platformAdmin.getPermissions().stream().map(Permission::getName).toList();

    assertThat(actualPermissions).containsExactlyInAnyOrderElementsOf(expectedPermissions);
  }

  @Test
  @DisplayName("Should assign minimal read permissions to Data Consumer")
  void shouldAssignMinimalPermissionsToDataConsumer() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    when(roleRepository.findAll()).thenReturn(List.of());

    // when
    roleInitializer.run(null);

    // then
    verify(roleRepository).saveAll(rolesCaptor.capture());
    Role dataConsumer = findRole(rolesCaptor.getValue(), "Data Consumer");

    List<String> permissionNames =
        dataConsumer.getPermissions().stream().map(Permission::getName).toList();

    assertThat(permissionNames).containsExactlyInAnyOrder("DATASET_READ", "DATASET_PAYLOAD_READ");
  }

  @Test
  @DisplayName("Should assign release permissions to Data Owner")
  void shouldAssignReleasePermissionsToDataOwner() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    when(roleRepository.findAll()).thenReturn(List.of());

    // when
    roleInitializer.run(null);

    // then
    verify(roleRepository).saveAll(rolesCaptor.capture());
    Role dataOwner = findRole(rolesCaptor.getValue(), "Data Owner");

    List<String> permissionNames =
        dataOwner.getPermissions().stream().map(Permission::getName).toList();

    assertThat(permissionNames)
        .contains("DATASET_RELEASE", "DATASOURCE_RELEASE", "DATASTRUCTURE_RELEASE");
  }

  @Test
  @DisplayName("Should assign governance permissions to Data Gatekeeper")
  void shouldAssignGovernancePermissionsToDataGatekeeper() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    when(roleRepository.findAll()).thenReturn(List.of());

    // when
    roleInitializer.run(null);

    // then
    verify(roleRepository).saveAll(rolesCaptor.capture());
    Role gatekeeper = findRole(rolesCaptor.getValue(), "Data Gatekeeper");

    List<String> permissionNames =
        gatekeeper.getPermissions().stream().map(Permission::getName).toList();

    assertThat(permissionNames)
        .containsExactlyInAnyOrder(
            "DATASET_READ",
            "DATASET_RELEASE",
            "DATASET_PAYLOAD_READ",
            "DATASOURCE_READ",
            "DATASOURCE_RELEASE",
            "DATASTRUCTURE_READ",
            "DATASTRUCTURE_RELEASE");
  }

  @Test
  @DisplayName("Should be idempotent when running multiple times")
  void shouldBeIdempotentWhenRunningMultipleTimes() {
    // given - first run with empty database
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    when(roleRepository.findAll()).thenReturn(List.of());

    // when - first run
    roleInitializer.run(null);

    // then - first run creates all
    verify(roleRepository).saveAll(rolesCaptor.capture());
    assertThat(rolesCaptor.getValue()).hasSize(StandardRole.values().length);

    // given - second run with all roles already in database
    List<Role> existingRoles = new ArrayList<>();
    for (StandardRole std : StandardRole.values()) {
      Role role = new Role();
      role.setName(std.roleName);
      existingRoles.add(role);
    }
    when(roleRepository.findAll()).thenReturn(existingRoles);

    // when - second run
    roleInitializer.run(null);

    // then - no additional saves (saveAll was only called once total)
    verify(roleRepository).saveAll(anyList());
  }

  private Role findRole(List<Role> roles, String name) {
    return roles.stream()
        .filter(r -> r.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Role not found: " + name));
  }
}
