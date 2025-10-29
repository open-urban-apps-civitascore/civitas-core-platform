package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.repository.specification.base.NamedEntitySpec;
import net.kaczmarzyk.spring.data.jpa.domain.*;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

/**
 * Permission filtering specification. Inherits: id, createdAt, modifiedAt, tenantId, title,
 * description, q from base specs.
 */
@Spec(path = "name", params = "name", spec = LikeIgnoreCase.class)
interface PermissionNameSpec extends NamedEntitySpec<Permission> {}

public interface PermissionSpec extends PermissionNameSpec {}
