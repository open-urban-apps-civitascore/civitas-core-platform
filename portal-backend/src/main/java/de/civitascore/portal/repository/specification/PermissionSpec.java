package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.repository.specification.base.NamedEntitySpec;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
import org.springframework.data.jpa.domain.Specification;

@Spec(path = "permissionType", params = "permissionType", spec = Equal.class)
interface PermissionTypeSpec extends Specification<Permission> {}

@Spec(path = "category", params = "category", spec = Equal.class)
interface PermissionCategorySpec extends Specification<Permission> {}

@Spec(path = "source", params = "source", spec = Equal.class)
interface PermissionSourceSpec extends Specification<Permission> {}

public interface PermissionSpec
    extends NamedEntitySpec<Permission>,
        PermissionTypeSpec,
        PermissionCategorySpec,
        PermissionSourceSpec {}
