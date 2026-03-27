package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.repository.PermissionRepository;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only service for querying {@link Permission} entities. Permissions are system-managed and
 * cannot be created or modified through this service; they are initialized at startup by {@link
 * de.civitascore.portal.service.initializer.PermissionInitializer}.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PermissionService {

  private final PermissionRepository permissionRepository;

  /**
   * Finds all permissions matching the given specification, sorted as requested.
   *
   * @param spec the filter specification
   * @param sort the sort order
   * @return list of matching permissions
   */
  public List<Permission> findAll(Specification<Permission> spec, Sort sort) {
    return permissionRepository.findAll(spec, sort);
  }

  /**
   * Finds a permission by ID or throws if not found.
   *
   * @param id the permission ID
   * @return the permission entity
   * @throws ResourceNotFoundException if no permission exists with the given ID
   */
  public Permission findByIdOrThrow(UUID id) {
    return permissionRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(Permission.class.getSimpleName(), id));
  }

  /**
   * Returns the underlying repository for direct access (e.g., batch lookups by ID).
   *
   * @return the permission repository
   */
  public PermissionRepository getRepository() {
    return permissionRepository;
  }
}
