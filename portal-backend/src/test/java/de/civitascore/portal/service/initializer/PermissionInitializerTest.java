package de.civitascore.portal.service.initializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.PermissionCategory;
import de.civitascore.portal.model.embedded.PermissionName;
import de.civitascore.portal.model.embedded.PermissionSource;
import de.civitascore.portal.model.embedded.PermissionType;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.repository.PermissionRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Permission Initializer Tests")
class PermissionInitializerTest {

  @Mock private PermissionRepository permissionRepository;

  @InjectMocks private PermissionInitializer permissionInitializer;

  @Captor private ArgumentCaptor<List<Permission>> permissionsCaptor;

  @Test
  @DisplayName("Should create all permissions when database is empty")
  void shouldCreateAllPermissionsWhenDatabaseIsEmpty() {
    // given
    when(permissionRepository.findAll()).thenReturn(List.of());

    // when
    permissionInitializer.run(null);

    // then
    verify(permissionRepository).saveAll(permissionsCaptor.capture());
    List<Permission> created = permissionsCaptor.getValue();
    assertThat(created).hasSize(PermissionName.values().length);
  }

  @Test
  @DisplayName("Should not create duplicates when all permissions already exist")
  void shouldNotCreateDuplicatesWhenAllPermissionsExist() {
    // given
    List<Permission> existing = allPermissionsFromEnum();
    when(permissionRepository.findAll()).thenReturn(existing);

    // when
    permissionInitializer.run(null);

    // then
    verify(permissionRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("Should only add missing permissions when some already exist")
  void shouldOnlyAddMissingPermissions() {
    // given
    Permission existingPermission = createPermission(PermissionName.USER_CREATE);
    when(permissionRepository.findAll()).thenReturn(List.of(existingPermission));

    // when
    permissionInitializer.run(null);

    // then
    verify(permissionRepository).saveAll(permissionsCaptor.capture());
    List<Permission> created = permissionsCaptor.getValue();
    assertThat(created).hasSize(PermissionName.values().length - 1);
    assertThat(created).noneMatch(p -> p.getName().equals("USER_CREATE"));
  }

  @Test
  @DisplayName("Should set correct permission type and category for system permissions")
  void shouldSetCorrectMetadataForSystemPermissions() {
    // given
    when(permissionRepository.findAll()).thenReturn(List.of());

    // when
    permissionInitializer.run(null);

    // then
    verify(permissionRepository).saveAll(permissionsCaptor.capture());
    List<Permission> created = permissionsCaptor.getValue();

    Permission userCreate =
        created.stream().filter(p -> p.getName().equals("USER_CREATE")).findFirst().orElseThrow();

    assertThat(userCreate.getPermissionType()).isEqualTo(PermissionType.SYSTEM);
    assertThat(userCreate.getCategory()).isEqualTo(PermissionCategory.TENANT_ADMINISTRATION);
    assertThat(userCreate.getSource()).isEqualTo(PermissionSource.INTERNAL);
  }

  @Test
  @DisplayName("Should set correct permission type and category for data permissions")
  void shouldSetCorrectMetadataForDataPermissions() {
    // given
    when(permissionRepository.findAll()).thenReturn(List.of());

    // when
    permissionInitializer.run(null);

    // then
    verify(permissionRepository).saveAll(permissionsCaptor.capture());
    List<Permission> created = permissionsCaptor.getValue();

    Permission datasetCreate =
        created.stream()
            .filter(p -> p.getName().equals("DATASET_CREATE"))
            .findFirst()
            .orElseThrow();

    assertThat(datasetCreate.getPermissionType()).isEqualTo(PermissionType.DATA);
    assertThat(datasetCreate.getCategory()).isEqualTo(PermissionCategory.DATA);
    assertThat(datasetCreate.getSource()).isEqualTo(PermissionSource.INTERNAL);
  }

  @Test
  @DisplayName("Should be idempotent when running multiple times")
  void shouldBeIdempotentWhenRunningMultipleTimes() {
    // given - first run with empty database
    when(permissionRepository.findAll()).thenReturn(List.of());

    // when - first run
    permissionInitializer.run(null);

    // then - first run creates all
    verify(permissionRepository).saveAll(permissionsCaptor.capture());
    List<Permission> firstRunCreated = permissionsCaptor.getValue();
    assertThat(firstRunCreated).hasSize(PermissionName.values().length);

    // given - second run with all permissions already in database
    when(permissionRepository.findAll()).thenReturn(allPermissionsFromEnum());

    // when - second run
    permissionInitializer.run(null);

    // then - no additional saves (saveAll was only called once total)
    verify(permissionRepository).saveAll(anyList());
  }

  @Test
  @DisplayName("Should create a permission for every PermissionName enum value")
  void shouldCreatePermissionForEveryEnumValue() {
    // given
    when(permissionRepository.findAll()).thenReturn(List.of());

    // when
    permissionInitializer.run(null);

    // then
    verify(permissionRepository).saveAll(permissionsCaptor.capture());
    List<Permission> created = permissionsCaptor.getValue();

    List<String> createdNames = created.stream().map(Permission::getName).toList();
    for (PermissionName permissionName : PermissionName.values()) {
      assertThat(createdNames).contains(permissionName.name());
    }
  }

  private Permission createPermission(PermissionName permissionName) {
    Permission permission = new Permission();
    permission.setName(permissionName.name());
    permission.setPermissionType(permissionName.getPermissionType());
    permission.setCategory(permissionName.getCategory());
    permission.setSource(permissionName.getSource());
    return permission;
  }

  private List<Permission> allPermissionsFromEnum() {
    List<Permission> permissions = new ArrayList<>();
    for (PermissionName name : PermissionName.values()) {
      permissions.add(createPermission(name));
    }
    return permissions;
  }
}
