package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.repository.specification.base.NamedEntitySpec;
import net.kaczmarzyk.spring.data.jpa.domain.LikeIgnoreCase;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

@Spec(path = "name", params = "name", spec = LikeIgnoreCase.class)
interface RoleNameSpec extends NamedEntitySpec<Role> {}

public interface RoleSpec extends RoleNameSpec {}
