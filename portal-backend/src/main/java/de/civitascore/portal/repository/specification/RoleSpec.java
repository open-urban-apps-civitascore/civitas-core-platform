package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.repository.specification.base.FilterParam;
import de.civitascore.portal.repository.specification.base.NamedEntitySpec;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.domain.In;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

@FilterParam(name = "roleType", description = "Filter by one or more role types (exact match).")
@Spec(path = "roleType", params = "roleType", paramSeparator = ',', spec = In.class)
interface RoleTypeSpec extends NamedEntitySpec<Role> {}

@FilterParam(name = "readonly", description = "Filter by readonly flag (exact match).")
@Spec(path = "readonly", params = "readonly", spec = Equal.class)
interface RoleReadonlySpec extends NamedEntitySpec<Role> {}

/** JPA Specification for filtering {@link Role} entities via query parameters. */
public interface RoleSpec extends RoleTypeSpec, RoleReadonlySpec {}
