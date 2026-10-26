package de.civitascore.portal.repository.specification;

import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import de.civitascore.portal.repository.specification.base.FilterParam;
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.domain.LikeIgnoreCase;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Or;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;

@FilterParam(name = "roleId", description = "Filter by role ID (exact match).")
@Spec(path = "role.id", params = "roleId", spec = Equal.class)
interface AssignmentRoleIdSpec extends BaseSpec<Assignment> {}

@FilterParam(
    name = "userId",
    description = "Filter by the ID of a member of the assigned group (exact match).")
@Spec(path = "group.members.id", params = "userId", spec = Equal.class)
interface AssignmentUserIdSpec extends BaseSpec<Assignment> {}

@FilterParam(name = "groupId", description = "Filter by group ID (exact match).")
@Spec(path = "group.id", params = "groupId", spec = Equal.class)
interface AssignmentGroupIdSpec extends BaseSpec<Assignment> {}

@FilterParam(
    name = "scopeId",
    description =
        "Filter by the ID of the scope: a data structure, data source, dataset or datapool"
            + " (exact match).")
@Or({
  @Spec(path = "dataStructure.id", params = "scopeId", spec = Equal.class),
  @Spec(path = "dataSource.id", params = "scopeId", spec = Equal.class),
  @Spec(path = "dataset.id", params = "scopeId", spec = Equal.class),
  @Spec(path = "dataPool.id", params = "scopeId", spec = Equal.class)
})
interface AssignmentScopeIdSpec extends BaseSpec<Assignment> {}

@FilterParam(name = "scopeType", description = "Filter by scope type (exact match).")
@FilterParam(name = "roleType", description = "Filter by role type (exact match).")
@Or({
  @Spec(path = "scopeType", params = "scopeType", spec = Equal.class),
  @Spec(path = "role.roleType", params = "roleType", spec = Equal.class)
})
interface AssignmentScopeTypeOrRoleTypeSpec extends BaseSpec<Assignment> {}

@FilterParam(
    name = "q",
    description =
        "Search in role name, role description, or group name (partial match, case-insensitive).")
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
