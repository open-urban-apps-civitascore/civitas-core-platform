package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.repository.specification.base.NamedEntitySpec;
import net.kaczmarzyk.spring.data.jpa.domain.LikeIgnoreCase;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

@Spec(path = "name", params = "name", spec = LikeIgnoreCase.class)
interface PermissionNameSpec extends NamedEntitySpec<Permission> {}

public interface PermissionSpec extends PermissionNameSpec {}
