package de.civitascore.portal.service.initializer;

import de.civitascore.portal.model.embedded.PermissionName;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.repository.PermissionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Initializes the permission table at application startup by synchronizing it with the {@link
 * PermissionName} enum. Creates any permissions that do not yet exist in the database.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionInitializer {

  private final PermissionRepository permissionRepository;

  /** Creates any permissions from {@link PermissionName} that are not yet persisted. */
  public void initialize() {
    Set<String> existingKeys =
        permissionRepository.findAll().stream()
            .map(p -> p.getName() + ":" + p.getSource())
            .collect(Collectors.toSet());

    List<Permission> toCreate = new ArrayList<>();

    for (PermissionName permissionName : PermissionName.values()) {
      String key = permissionName.name() + ":" + permissionName.getSource();
      if (!existingKeys.contains(key)) {
        Permission permission = new Permission();
        permission.setName(permissionName.name());
        permission.setPermissionType(permissionName.getPermissionType());
        permission.setCategory(permissionName.getCategory());
        permission.setSource(permissionName.getSource());
        toCreate.add(permission);
      }
    }

    if (!toCreate.isEmpty()) {
      permissionRepository.saveAll(toCreate);
      log.info("Initialized {} new permissions", toCreate.size());
    } else {
      log.info("All {} permissions already exist", PermissionName.values().length);
    }
  }
}
