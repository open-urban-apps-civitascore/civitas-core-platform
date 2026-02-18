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

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PermissionService {

  private final PermissionRepository permissionRepository;

  public List<Permission> findAll(Specification<Permission> spec, Sort sort) {
    return permissionRepository.findAll(spec, sort);
  }

  public Permission findByIdOrThrow(UUID id) {
    return permissionRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(Permission.class.getSimpleName(), id));
  }

  public PermissionRepository getRepository() {
    return permissionRepository;
  }
}
