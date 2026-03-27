package de.civitascore.portal.service.initializer;

import de.civitascore.portal.model.embedded.PermissionName;
import de.civitascore.portal.model.embedded.RoleDefault;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.repository.PermissionRepository;
import de.civitascore.portal.repository.RoleRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Initializes the role table at application startup by synchronizing it with the {@link
 * RoleDefault} enum. Creates missing roles, updates roles whose permissions or descriptions have
 * changed, and removes obsolete readonly roles no longer defined in the enum.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleInitializer {

  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;

  /**
   * Synchronizes roles in the database with the definitions in {@link RoleDefault}. Creates new
   * roles, updates changed roles, and removes obsolete readonly roles.
   */
  public void initialize() {
    Map<String, Permission> permissionsByNameAndSource =
        permissionRepository.findAll().stream()
            .collect(Collectors.toMap(p -> p.getName() + ":" + p.getSource(), Function.identity()));

    Map<String, Role> existingRolesByName =
        roleRepository.findAll().stream()
            .collect(Collectors.toMap(Role::getName, Function.identity()));

    Set<String> standardRoleNames =
        Arrays.stream(RoleDefault.values())
            .map(RoleDefault::getRoleName)
            .collect(Collectors.toSet());

    List<Role> toCreate = new ArrayList<>();
    List<Role> toUpdate = new ArrayList<>();
    List<Role> toRemove =
        existingRolesByName.values().stream()
            .filter(role -> role.isReadonly() && !standardRoleNames.contains(role.getName()))
            .collect(Collectors.toCollection(ArrayList::new));

    for (RoleDefault standardRole : RoleDefault.values()) {
      Set<Permission> expectedPermissions =
          resolvePermissions(standardRole.getPermissions(), permissionsByNameAndSource);
      Role existing = existingRolesByName.get(standardRole.getRoleName());

      if (existing == null) {
        Role role = new Role();
        role.setName(standardRole.getRoleName());
        role.setDescription(standardRole.getDescription());
        role.setRoleType(standardRole.getRoleType());
        role.setReadonly(true);
        role.setPermissions(expectedPermissions);
        toCreate.add(role);
      } else if (needsUpdate(existing, standardRole, expectedPermissions)) {
        existing.setDescription(standardRole.getDescription());
        existing.setPermissions(expectedPermissions);
        toUpdate.add(existing);
      }
    }

    if (!toCreate.isEmpty()) {
      roleRepository.saveAll(toCreate);
      log.info("Initialized {} new roles", toCreate.size());
    }
    if (!toUpdate.isEmpty()) {
      roleRepository.saveAll(toUpdate);
      log.info("Updated {} existing roles", toUpdate.size());
    }
    if (!toRemove.isEmpty()) {
      roleRepository.deleteAll(toRemove);
      log.info("Removed {} obsolete readonly roles", toRemove.size());
    }
    if (toCreate.isEmpty() && toUpdate.isEmpty() && toRemove.isEmpty()) {
      log.info("All {} roles are up to date", RoleDefault.values().length);
    }
  }

  private boolean needsUpdate(
      Role existing, RoleDefault standardRole, Set<Permission> expectedPermissions) {
    if (!Objects.equals(existing.getDescription(), standardRole.getDescription())) {
      return true;
    }
    Set<String> existingPermissionNames =
        existing.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());
    Set<String> expectedPermissionNames =
        expectedPermissions.stream().map(Permission::getName).collect(Collectors.toSet());
    return !existingPermissionNames.equals(expectedPermissionNames);
  }

  private Set<Permission> resolvePermissions(
      PermissionName[] names, Map<String, Permission> permissionsByNameAndSource) {
    Set<Permission> permissions = new HashSet<>();
    for (PermissionName name : names) {
      String key = name.name() + ":" + name.getSource();
      Permission permission = permissionsByNameAndSource.get(key);
      if (permission == null) {
        throw new IllegalStateException(
            "Permission "
                + name.name()
                + " with source "
                + name.getSource()
                + " not found in database");
      }
      permissions.add(permission);
    }
    return permissions;
  }
}
