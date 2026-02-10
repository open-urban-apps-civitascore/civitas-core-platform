package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Or;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

@Spec(path = "role.id", params = "roleId", spec = Equal.class)
interface AssignmentRoleIdSpec extends BaseSpec<Assignment> {}

@Spec(path = "user.id", params = "userId", spec = Equal.class)
interface AssignmentUserIdSpec extends BaseSpec<Assignment> {}

@Spec(path = "group.id", params = "groupId", spec = Equal.class)
interface AssignmentGroupIdSpec extends BaseSpec<Assignment> {}

@Or({
  @Spec(path = "dataStructure.id", params = "scopeId", spec = Equal.class),
  @Spec(path = "dataSource.id", params = "scopeId", spec = Equal.class),
  @Spec(path = "dataset.id", params = "scopeId", spec = Equal.class),
  @Spec(path = "dataSpace.id", params = "scopeId", spec = Equal.class),
  @Spec(path = "catalog.id", params = "scopeId", spec = Equal.class)
})
interface AssignmentScopeIdSpec extends BaseSpec<Assignment> {}

public interface AssignmentSpec
    extends AssignmentRoleIdSpec,
        AssignmentUserIdSpec,
        AssignmentGroupIdSpec,
        AssignmentScopeIdSpec {}
