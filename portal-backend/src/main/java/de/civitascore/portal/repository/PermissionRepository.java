package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Permission;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Permission} entities. */
@Repository
public interface PermissionRepository extends NamedEntityRepository<Permission, UUID> {}
