package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.repository.specification.base.FilterParam;
import de.civitascore.portal.repository.specification.base.NamedEntitySpec;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.springframework.data.jpa.domain.Specification;

@FilterParam(name = "permissionType", description = "Filter by permission type (exact match).")
@Spec(path = "permissionType", params = "permissionType", spec = Equal.class)
interface PermissionTypeSpec extends Specification<Permission> {}

@FilterParam(name = "category", description = "Filter by category (exact match).")
@Spec(path = "category", params = "category", spec = Equal.class)
interface PermissionCategorySpec extends Specification<Permission> {}

@FilterParam(name = "source", description = "Filter by source (exact match).")
@Spec(path = "source", params = "source", spec = Equal.class)
interface PermissionSourceSpec extends Specification<Permission> {}

/** JPA Specification for filtering {@link Permission} entities via query parameters. */
public interface PermissionSpec
    extends NamedEntitySpec<Permission>,
        PermissionTypeSpec,
        PermissionCategorySpec,
        PermissionSourceSpec {}
