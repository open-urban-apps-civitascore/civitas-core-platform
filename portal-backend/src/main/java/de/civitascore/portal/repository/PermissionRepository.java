package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Permission;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepository extends NamedEntityRepository<Permission, String> {}
