package de.civitascore.portal.repository;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentRepository extends BaseRepository<Assignment, UUID> {

  /**
   * Find an assignment by ID with all related entities eagerly fetched in a single JOIN query. This
   * prevents N+1 query problems when loading assignments with their relationships. Use this for
   * read operations (GET/UPDATE) instead of regular findById.
   *
   * @param id the assignment ID
   * @return the assignment with eagerly fetched group, and role
   */
  @EntityGraph(attributePaths = {"group", "role"})
  @Query("SELECT a FROM Assignment a WHERE a.id = :id")
  Optional<Assignment> findByIdWithRelations(@Param("id") UUID id);

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

  @EntityGraph(
      attributePaths = {
        "group",
        "role",
        "dataSource",
        "dataset",
        "dataSpace",
        "catalog",
        "dataStructure"
      })
  @Query(
      "SELECT a FROM Assignment a WHERE a.scopeType = :scopeType "
          + "AND CASE a.scopeType "
          + "WHEN ScopeType.DATASOURCE THEN a.dataSource.id "
          + "WHEN ScopeType.DATASET THEN a.dataset.id "
          + "WHEN ScopeType.DATASPACE THEN a.dataSpace.id "
          + "WHEN ScopeType.CATALOG THEN a.catalog.id "
          + "WHEN ScopeType.DATASTRUCTURE THEN a.dataStructure.id "
          + "END = :scopeId")
  List<Assignment> findAllByScopeTypeAndScopeId(
      @Param("scopeType") ScopeType scopeType, @Param("scopeId") UUID scopeId);
}
