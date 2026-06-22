package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.domain.LikeIgnoreCase;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Or;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

@Spec(path = "role.id", params = "roleId", spec = Equal.class)
interface AssignmentRoleIdSpec extends BaseSpec<Assignment> {}

@Spec(path = "group.members.id", params = "userId", spec = Equal.class)
interface AssignmentUserIdSpec extends BaseSpec<Assignment> {}

@Spec(path = "group.id", params = "groupId", spec = Equal.class)
interface AssignmentGroupIdSpec extends BaseSpec<Assignment> {}

@Or({
  @Spec(path = "dataStructure.id", params = "scopeId", spec = Equal.class),
  @Spec(path = "dataSource.id", params = "scopeId", spec = Equal.class),
  @Spec(path = "dataset.id", params = "scopeId", spec = Equal.class),
  @Spec(path = "catalog.id", params = "scopeId", spec = Equal.class),
  @Spec(path = "dataPool.id", params = "scopeId", spec = Equal.class)
})
interface AssignmentScopeIdSpec extends BaseSpec<Assignment> {}

@Or({
  @Spec(path = "scopeType", params = "scopeType", spec = Equal.class),
  @Spec(path = "role.roleType", params = "roleType", spec = Equal.class)
})
interface AssignmentScopeTypeOrRoleTypeSpec extends BaseSpec<Assignment> {}

@Or({
  @Spec(path = "role.name", params = "q", spec = LikeIgnoreCase.class),
  @Spec(path = "role.description", params = "q", spec = LikeIgnoreCase.class),
  @Spec(path = "group.name", params = "q", spec = LikeIgnoreCase.class)
})
interface AssignmentQuickSearchSpec extends BaseSpec<Assignment> {}

/** JPA Specification for filtering {@link Assignment} entities via query parameters. */
public interface AssignmentSpec
    extends AssignmentRoleIdSpec,
        AssignmentUserIdSpec,
        AssignmentGroupIdSpec,
        AssignmentScopeIdSpec,
        AssignmentScopeTypeOrRoleTypeSpec,
        AssignmentQuickSearchSpec {}
