package de.civitascore.portal.service.initializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.PermissionCategory;
import de.civitascore.portal.model.embedded.PermissionName;
import de.civitascore.portal.model.embedded.RoleDefault;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.repository.PermissionRepository;
import de.civitascore.portal.repository.RoleRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    roleInitializer.initialize();

    // then
    verify(roleRepository).saveAll(rolesCaptor.capture());
    List<Role> created = rolesCaptor.getValue();
    assertThat(created).hasSize(RoleDefault.values().length);
  }

  @Test
  @DisplayName("Should not create or update when all roles already match")
  void shouldNotCreateOrUpdateWhenAllRolesMatch() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    when(roleRepository.findAll()).thenReturn(allMatchingRoles());

    // when
    roleInitializer.initialize();

    // then
    verify(roleRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("Should only add missing roles when some already exist")
  void shouldOnlyAddMissingRoles() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    Role existingRole = buildMatchingRole(RoleDefault.TENANT_ADMIN);
    when(roleRepository.findAll()).thenReturn(List.of(existingRole));

    // when
    roleInitializer.initialize();

    // then
    verify(roleRepository).saveAll(rolesCaptor.capture());
    List<Role> created = rolesCaptor.getValue();
    assertThat(created).hasSize(RoleDefault.values().length - 1);
    assertThat(created).noneMatch(r -> r.getName().equals("Tenant Admin"));
  }

  @Test
  @DisplayName("Should mark all created roles as readonly")
  void shouldMarkAllRolesAsReadonly() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    when(roleRepository.findAll()).thenReturn(List.of());

    // when
    roleInitializer.initialize();

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
    roleInitializer.initialize();

    // then
    verify(roleRepository).saveAll(rolesCaptor.capture());
    List<Role> created = rolesCaptor.getValue();

    assertThat(findRole(created, "Tenant Admin").getRoleType()).isEqualTo(RoleType.SYSTEM);
    assertThat(findRole(created, "Data Architect").getRoleType()).isEqualTo(RoleType.DATA);
    assertThat(findRole(created, "Data Consumer").getRoleType()).isEqualTo(RoleType.DATA);
    assertThat(findRole(created, "Data Steward").getRoleType()).isEqualTo(RoleType.DATA);
    assertThat(findRole(created, "Data Owner").getRoleType()).isEqualTo(RoleType.DATA);
    assertThat(findRole(created, "Data Gatekeeper").getRoleType()).isEqualTo(RoleType.DATA);
  }

  @Test
  @DisplayName("Should assign all tenant administration permissions to Tenant Admin")
  void shouldAssignAllTenantAdminPermissionsToTenantAdmin() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    when(roleRepository.findAll()).thenReturn(List.of());

    // when
    roleInitializer.initialize();

    // then
    verify(roleRepository).saveAll(rolesCaptor.capture());
    Role tenantAdmin = findRole(rolesCaptor.getValue(), "Tenant Admin");

    List<String> expectedPermissions =
        allPermissions.stream()
            .filter(p -> p.getCategory() == PermissionCategory.TENANT_ADMINISTRATION)
            .map(Permission::getName)
            .toList();

    List<String> actualPermissions =
        tenantAdmin.getPermissions().stream().map(Permission::getName).toList();

    assertThat(actualPermissions).containsExactlyInAnyOrderElementsOf(expectedPermissions);
  }

  @Test
  @DisplayName("Should assign minimal read permissions to Data Consumer")
  void shouldAssignMinimalPermissionsToDataConsumer() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    when(roleRepository.findAll()).thenReturn(List.of());

    // when
    roleInitializer.initialize();

    // then
    verify(roleRepository).saveAll(rolesCaptor.capture());
    Role dataConsumer = findRole(rolesCaptor.getValue(), "Data Consumer");

    List<String> permissionNames =
        dataConsumer.getPermissions().stream().map(Permission::getName).toList();

    assertThat(permissionNames)
        .containsExactlyInAnyOrder("DATASET_READ", "DATASET_PAYLOAD_READ", "DATAPOOL_READ");
  }

  @Test
  @DisplayName("Should assign release permissions to Data Owner")
  void shouldAssignReleasePermissionsToDataOwner() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    when(roleRepository.findAll()).thenReturn(List.of());

    // when
    roleInitializer.initialize();

    // then
    verify(roleRepository).saveAll(rolesCaptor.capture());
    Role dataOwner = findRole(rolesCaptor.getValue(), "Data Owner");

    List<String> permissionNames =
        dataOwner.getPermissions().stream().map(Permission::getName).toList();

    assertThat(permissionNames)
        .contains("DATASET_RELEASE", "DATASOURCE_RELEASE", "DATASTRUCTURE_RELEASE");
  }

  @Test
  @DisplayName("Should assign data permissions to Data Gatekeeper")
  void shouldAssignDataPermissionsToDataGatekeeper() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    when(roleRepository.findAll()).thenReturn(List.of());

    // when
    roleInitializer.initialize();

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
            "DATASTRUCTURE_RELEASE",
            "DATAPOOL_READ",
            "INSTALLATION_READ");
  }

  @Test
  @DisplayName("Should give the permission to uninstall to Data Owner only")
  void shouldGiveInstallationDeleteToDataOwnerOnly() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    when(roleRepository.findAll()).thenReturn(List.of());

    // when
    roleInitializer.initialize();

    // then
    verify(roleRepository).saveAll(rolesCaptor.capture());
    List<String> rolesWithPermission =
        rolesCaptor.getValue().stream()
            .filter(
                role ->
                    role.getPermissions().stream()
                        .anyMatch(permission -> "INSTALLATION_DELETE".equals(permission.getName())))
            .map(Role::getName)
            .toList();

    assertThat(rolesWithPermission).containsExactly("Data Owner");
  }

  @Test
  @DisplayName("Should be idempotent when running multiple times")
  void shouldBeIdempotentWhenRunningMultipleTimes() {
    // given - first run with empty database
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    when(roleRepository.findAll()).thenReturn(List.of());

    // when - first run
    roleInitializer.initialize();

    // then - first run creates all
    verify(roleRepository).saveAll(rolesCaptor.capture());
    assertThat(rolesCaptor.getValue()).hasSize(RoleDefault.values().length);

    // given - second run with all roles matching
    when(roleRepository.findAll()).thenReturn(allMatchingRoles());

    // when - second run
    roleInitializer.initialize();

    // then - no additional saves (saveAll was only called once total)
    verify(roleRepository).saveAll(anyList());
  }

  @Test
  @DisplayName("Should update existing role when description changes")
  void shouldUpdateExistingRoleWhenDescriptionChanges() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    Role existingRole = buildMatchingRole(RoleDefault.DATA_CONSUMER);
    existingRole.setDescription("Outdated description");
    when(roleRepository.findAll()).thenReturn(List.of(existingRole));

    // when
    roleInitializer.initialize();

    // then - two saveAll calls: one for new roles, one for updated roles
    verify(roleRepository).saveAll(List.of(existingRole));
    assertThat(existingRole.getDescription()).isEqualTo(RoleDefault.DATA_CONSUMER.getDescription());
  }

  @Test
  @DisplayName("Should update existing role when permissions change")
  void shouldUpdateExistingRoleWhenPermissionsChange() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    Role existingRole = buildMatchingRole(RoleDefault.DATA_CONSUMER);
    existingRole.setPermissions(new HashSet<>()); // empty permissions
    when(roleRepository.findAll()).thenReturn(List.of(existingRole));

    // when
    roleInitializer.initialize();

    // then
    verify(roleRepository).saveAll(List.of(existingRole));
    Set<String> permissionNames =
        existingRole.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());
    assertThat(permissionNames)
        .containsExactlyInAnyOrder("DATASET_READ", "DATASET_PAYLOAD_READ", "DATAPOOL_READ");
  }

  @Test
  @DisplayName("Should remove obsolete readonly roles not in standard definitions")
  void shouldRemoveObsoleteReadonlyRoles() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    List<Role> existingRoles = new ArrayList<>(allMatchingRoles());
    Role obsoleteRole = new Role();
    obsoleteRole.setName("Obsolete Role");
    obsoleteRole.setReadonly(true);
    existingRoles.add(obsoleteRole);
    when(roleRepository.findAll()).thenReturn(existingRoles);

    // when
    roleInitializer.initialize();

    // then
    verify(roleRepository).deleteAll(List.of(obsoleteRole));
  }

  @Test
  @DisplayName("Should not remove non-readonly roles that are not in standard definitions")
  void shouldNotRemoveNonReadonlyRoles() {
    // given
    when(permissionRepository.findAll()).thenReturn(allPermissions);
    List<Role> existingRoles = new ArrayList<>(allMatchingRoles());
    Role customRole = new Role();
    customRole.setName("Custom User Role");
    customRole.setReadonly(false);
    existingRoles.add(customRole);
    when(roleRepository.findAll()).thenReturn(existingRoles);

    // when
    roleInitializer.initialize();

    // then
    verify(roleRepository, never()).deleteAll(anyList());
  }

  private Role findRole(List<Role> roles, String name) {
    return roles.stream()
        .filter(r -> r.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Role not found: " + name));
  }

  private Role buildMatchingRole(RoleDefault standardRole) {
    Map<String, Permission> permissionsByName =
        allPermissions.stream().collect(Collectors.toMap(Permission::getName, Function.identity()));
    Role role = new Role();
    role.setName(standardRole.getRoleName());
    role.setDescription(standardRole.getDescription());
    role.setRoleType(standardRole.getRoleType());
    role.setReadonly(true);
    Set<Permission> permissions = new HashSet<>();
    for (PermissionName pn : standardRole.getPermissions()) {
      Permission p = permissionsByName.get(pn.name());
      if (p != null) {
        permissions.add(p);
      }
    }
    role.setPermissions(permissions);
    return role;
  }

  private List<Role> allMatchingRoles() {
    List<Role> roles = new ArrayList<>();
    for (RoleDefault std : RoleDefault.values()) {
      roles.add(buildMatchingRole(std));
    }
    return roles;
  }
}
