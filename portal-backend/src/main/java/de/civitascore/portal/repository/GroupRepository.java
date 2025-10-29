package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Group;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends TenantAwareRepository<Group, String> {
  Optional<Group> findByTitleAndTenantId(String title, String tenantId);

  List<Group> findByParentGroupIsNullAndTenantId(String tenantId);

  @EntityGraph("Group.withMembers")
  Optional<Group> findWithMembersById(String id);

  @EntityGraph("Group.withSystemRoles")
  Optional<Group> findWithSystemRolesById(String id);
}
