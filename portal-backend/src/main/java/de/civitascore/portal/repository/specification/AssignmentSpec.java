package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.repository.specification.base.TenantAwareSpec;
import net.kaczmarzyk.spring.data.jpa.domain.*;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

/**
 * Assignment filtering specification. Inherits: id, createdAt, modifiedAt, tenantId from base
 * specs.
 */
@Spec(path = "role.id", params = "roleId", spec = Equal.class)
interface AssignmentRoleIdSpec extends TenantAwareSpec<Assignment> {}

@Spec(path = "user.id", params = "userId", spec = Equal.class)
interface AssignmentUserIdSpec extends TenantAwareSpec<Assignment> {}

@Spec(path = "group.id", params = "groupId", spec = Equal.class)
interface AssignmentGroupIdSpec extends TenantAwareSpec<Assignment> {}

public interface AssignmentSpec
    extends AssignmentRoleIdSpec, AssignmentUserIdSpec, AssignmentGroupIdSpec {}
