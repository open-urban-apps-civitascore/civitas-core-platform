package de.civitascore.portal.service.initializer;

import de.civitascore.portal.model.embedded.PermissionName;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class RoleInitializer implements ApplicationRunner {

  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    Map<String, Permission> permissionsByNameAndSource =
        permissionRepository.findAll().stream()
            .collect(Collectors.toMap(p -> p.getName() + ":" + p.getSource(), Function.identity()));

    Set<String> existingRoleNames =
        roleRepository.findAll().stream().map(Role::getName).collect(Collectors.toSet());

    List<Role> toCreate = new ArrayList<>();

    for (StandardRole standardRole : StandardRole.values()) {
      if (!existingRoleNames.contains(standardRole.roleName)) {
        Role role = new Role();
        role.setName(standardRole.roleName);
        role.setDescription(standardRole.description);
        role.setRoleType(standardRole.roleType);
        role.setReadonly(true);
        role.setPermissions(
            resolvePermissions(standardRole.permissions, permissionsByNameAndSource));
        toCreate.add(role);
      }
    }

    if (!toCreate.isEmpty()) {
      roleRepository.saveAll(toCreate);
      log.info("Initialized {} new roles", toCreate.size());
    } else {
      log.info("All {} roles already exist", StandardRole.values().length);
    }
  }

  private Set<Permission> resolvePermissions(
      PermissionName[] names, Map<String, Permission> permissionsByNameAndSource) {
    Set<Permission> permissions = new HashSet<>();
    for (PermissionName name : names) {
      String key = name.name() + ":" + name.getSource();
      Permission permission = permissionsByNameAndSource.get(key);
      if (permission != null) {
        permissions.add(permission);
      } else {
        log.warn(
            "Permission {} with source {} not found in database, skipping",
            name.name(),
            name.getSource());
      }
    }
    return permissions;
  }

  /** Standard role definitions as described in the Authorization Data Model documentation. */
  enum StandardRole {
    PLATFORM_ADMIN(
        "Platform Admin",
        "Management rights for the configuration of tenants and basic system parameters.",
        RoleType.SYSTEM,
        new PermissionName[] {
          PermissionName.USER_CREATE,
          PermissionName.USER_READ,
          PermissionName.USER_UPDATE,
          PermissionName.USER_DELETE,
          PermissionName.ROLE_CREATE,
          PermissionName.ROLE_READ,
          PermissionName.ROLE_UPDATE,
          PermissionName.ROLE_DELETE,
          PermissionName.ASSIGNMENT_CREATE,
          PermissionName.ASSIGNMENT_READ,
          PermissionName.ASSIGNMENT_DELETE,
          PermissionName.GROUP_CREATE,
          PermissionName.GROUP_READ,
          PermissionName.GROUP_UPDATE,
          PermissionName.GROUP_DELETE,
          PermissionName.PERMISSION_READ,
        }),

    TENANT_ADMIN(
        "Tenant Admin",
        "Tenant-wide permissions to manage users, roles and permissions as well as tenant"
            + " parameters.",
        RoleType.SYSTEM,
        new PermissionName[] {
          PermissionName.USER_CREATE,
          PermissionName.USER_READ,
          PermissionName.USER_UPDATE,
          PermissionName.USER_DELETE,
          PermissionName.ROLE_CREATE,
          PermissionName.ROLE_READ,
          PermissionName.ROLE_UPDATE,
          PermissionName.ROLE_DELETE,
          PermissionName.ASSIGNMENT_CREATE,
          PermissionName.ASSIGNMENT_READ,
          PermissionName.ASSIGNMENT_DELETE,
          PermissionName.GROUP_CREATE,
          PermissionName.GROUP_READ,
          PermissionName.GROUP_UPDATE,
          PermissionName.GROUP_DELETE,
          PermissionName.PERMISSION_READ,
        }),

    DATA_ARCHITECT(
        "Data Architect",
        "Management rights for all aspects of data ingestion, data processing, data storage and"
            + " data output.",
        RoleType.DATA,
        new PermissionName[] {
          PermissionName.DATASET_CREATE,
          PermissionName.DATASET_READ,
          PermissionName.DATASET_UPDATE,
          PermissionName.DATASET_DELETE,
          PermissionName.DATASOURCE_CREATE,
          PermissionName.DATASOURCE_READ,
          PermissionName.DATASOURCE_UPDATE,
          PermissionName.DATASOURCE_DELETE,
          PermissionName.DATASTRUCTURE_CREATE,
          PermissionName.DATASTRUCTURE_READ,
          PermissionName.DATASTRUCTURE_UPDATE,
          PermissionName.DATASTRUCTURE_DELETE,
        }),

    DATA_CONSUMER(
        "Data Consumer",
        "Read access to published data products.",
        RoleType.DATA,
        new PermissionName[] {
          PermissionName.DATASET_READ, PermissionName.DATASET_PAYLOAD_READ,
        }),

    DATA_STEWARD(
        "Data Steward",
        "Responsibility for the life-cycle of domain specific data in a data space.",
        RoleType.DATA,
        new PermissionName[] {
          PermissionName.DATASET_CREATE,
          PermissionName.DATASET_READ,
          PermissionName.DATASET_UPDATE,
          PermissionName.DATASET_DELETE,
          PermissionName.DATASET_PAYLOAD_CREATE,
          PermissionName.DATASET_PAYLOAD_READ,
          PermissionName.DATASET_PAYLOAD_UPDATE,
          PermissionName.DATASET_PAYLOAD_DELETE,
          PermissionName.DATASOURCE_CREATE,
          PermissionName.DATASOURCE_READ,
          PermissionName.DATASOURCE_UPDATE,
          PermissionName.DATASOURCE_DELETE,
          PermissionName.DATASTRUCTURE_CREATE,
          PermissionName.DATASTRUCTURE_READ,
          PermissionName.DATASTRUCTURE_UPDATE,
          PermissionName.DATASTRUCTURE_DELETE,
        }),

    DATA_OWNER(
        "Data Owner",
        "Business responsibility for one or several domains.",
        RoleType.DATA,
        new PermissionName[] {
          PermissionName.DATASET_CREATE,
          PermissionName.DATASET_READ,
          PermissionName.DATASET_UPDATE,
          PermissionName.DATASET_DELETE,
          PermissionName.DATASET_RELEASE,
          PermissionName.DATASET_PAYLOAD_CREATE,
          PermissionName.DATASET_PAYLOAD_READ,
          PermissionName.DATASET_PAYLOAD_UPDATE,
          PermissionName.DATASET_PAYLOAD_DELETE,
          PermissionName.DATASOURCE_CREATE,
          PermissionName.DATASOURCE_READ,
          PermissionName.DATASOURCE_UPDATE,
          PermissionName.DATASOURCE_DELETE,
          PermissionName.DATASOURCE_RELEASE,
          PermissionName.DATASTRUCTURE_CREATE,
          PermissionName.DATASTRUCTURE_READ,
          PermissionName.DATASTRUCTURE_UPDATE,
          PermissionName.DATASTRUCTURE_DELETE,
          PermissionName.DATASTRUCTURE_RELEASE,
        }),

    DATA_GATEKEEPER(
        "Data Gatekeeper",
        "Responsibility for data protection and data governance.",
        RoleType.GOVERNANCE,
        new PermissionName[] {
          PermissionName.DATASET_READ,
          PermissionName.DATASET_RELEASE,
          PermissionName.DATASET_PAYLOAD_READ,
          PermissionName.DATASOURCE_READ,
          PermissionName.DATASOURCE_RELEASE,
          PermissionName.DATASTRUCTURE_READ,
          PermissionName.DATASTRUCTURE_RELEASE,
        });

    final String roleName;
    final String description;
    final RoleType roleType;
    final PermissionName[] permissions;

    StandardRole(
        String roleName, String description, RoleType roleType, PermissionName[] permissions) {
      this.roleName = roleName;
      this.description = description;
      this.roleType = roleType;
      this.permissions = permissions;
    }
  }
}
