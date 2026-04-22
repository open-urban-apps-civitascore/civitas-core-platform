package de.civitascore.portal.repository;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Assignment} entities. */
@Repository
public interface AssignmentRepository extends BaseRepository<Assignment, UUID> {

  /**
   * Find an assignment by ID with all related entities eagerly fetched. This prevents N+1 query
   * problems when loading assignments with their relationships.
   *
   * @param id the assignment ID
   * @return the assignment with eagerly fetched group, role, and scope entities
   */
  @EntityGraph(
      attributePaths = {
        "group",
        "role",
        "dataSpace",
        "dataset",
        "catalog",
        "dataSource",
        "dataStructure"
      })
  @Override
  @NonNull Optional<Assignment> findById(@NonNull UUID id);

  /**
   * Find all assignments for the given role ID with groups eagerly fetched. This prevents N+1 query
   * problems when loading assignments with their roles.
   *
   * @param roleId the role ID
   * @return the assignments with eagerly fetched groups
   */
  @EntityGraph(attributePaths = {"group"})
  @Query("SELECT a FROM Assignment a WHERE a.role.id = :roleId")
  List<Assignment> findAllByRoleId(@Param("roleId") UUID roleId);

  /**
   * Find all assignments for the given group ID with groups eagerly fetched. This prevents N+1
   * query problems when loading assignments with their groups.
   *
   * @param groupId the group ID
   * @return the assignments with eagerly fetched roles
   */
  @EntityGraph(attributePaths = {"role"})
  @Query("SELECT a FROM Assignment a WHERE a.group.id = :groupId")
  List<Assignment> findAllByGroupId(@Param("groupId") UUID groupId);

  /**
   * Find all assignments for a data source with the given scope type.
   *
   * @param scopeType the scope type to filter by
   * @param dataSourceId the data source ID
   * @return assignments with eagerly fetched group, role, and dataSource
   */
  @EntityGraph(attributePaths = {"group", "role", "dataSource"})
  List<Assignment> findAllByScopeTypeAndDataSourceId(ScopeType scopeType, UUID dataSourceId);

  /**
   * Find all assignments for a dataset with the given scope type.
   *
   * @param scopeType the scope type to filter by
   * @param datasetId the dataset ID
   * @return assignments with eagerly fetched group, role, and dataset
   */
  @EntityGraph(attributePaths = {"group", "role", "dataset"})
  List<Assignment> findAllByScopeTypeAndDatasetId(ScopeType scopeType, UUID datasetId);

  /**
   * Find all assignments for a data space with the given scope type.
   *
   * @param scopeType the scope type to filter by
   * @param dataSpaceId the data space ID
   * @return assignments with eagerly fetched group, role, and dataSpace
   */
  @EntityGraph(attributePaths = {"group", "role", "dataSpace"})
  List<Assignment> findAllByScopeTypeAndDataSpaceId(ScopeType scopeType, UUID dataSpaceId);

  /**
   * Find all assignments for a catalog with the given scope type.
   *
   * @param scopeType the scope type to filter by
   * @param catalogId the catalog ID
   * @return assignments with eagerly fetched group, role, and catalog
   */
  @EntityGraph(attributePaths = {"group", "role", "catalog"})
  List<Assignment> findAllByScopeTypeAndCatalogId(ScopeType scopeType, UUID catalogId);

  /**
   * Find all assignments for a data structure with the given scope type.
   *
   * @param scopeType the scope type to filter by
   * @param dataStructureId the data structure ID
   * @return assignments with eagerly fetched group, role, and dataStructure
   */
  @EntityGraph(attributePaths = {"group", "role", "dataStructure"})
  List<Assignment> findAllByScopeTypeAndDataStructureId(ScopeType scopeType, UUID dataStructureId);

  /**
   * Check if an unscoped assignment exists for the given group and role.
   *
   * @param group the group
   * @param role the role
   * @return true if an unscoped assignment exists
   */
  boolean existsByGroupAndRoleAndScopeTypeIsNull(Group group, Role role);

  /**
   * Check if a scoped assignment exists for the given group, role, and scope type.
   *
   * @param group the group
   * @param role the role
   * @param scopeType the scope type
   * @return true if a matching scoped assignment exists
   */
  boolean existsByGroupAndRoleAndScopeType(Group group, Role role, ScopeType scopeType);

  /**
   * Find all assignments for a user identified by their external identity provider ID. Resolves
   * assignments via the user's group memberships with all related entities eagerly fetched.
   *
   * @param externalId the user's external identity provider ID (e.g., Keycloak subject)
   * @return assignments with eagerly fetched group, role, permissions, and scope entities
   */
  @EntityGraph(
      attributePaths = {
        "group",
        "role",
        "role.permissions",
        "dataStructure",
        "dataSource",
        "dataset",
        "dataSpace",
        "catalog"
      })
  @Query(
      "SELECT a FROM Assignment a"
          + " WHERE a.group IN (SELECT ug FROM User u JOIN u.groups ug"
          + " WHERE u.externalId = :externalId)")
  List<Assignment> findAllByUserExternalId(@Param("externalId") String externalId);
}
