package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.repository.specification.base.NamedEntitySpec;
import net.kaczmarzyk.spring.data.jpa.domain.In;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

@Spec(path = "roleType", params = "roleType", paramSeparator = ',', spec = In.class)
interface RoleTypeSpec extends NamedEntitySpec<Role> {}

/** JPA Specification for filtering {@link Role} entities via query parameters. */
public interface RoleSpec extends RoleTypeSpec {}
