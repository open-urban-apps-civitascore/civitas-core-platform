package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Group;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends NamedEntityRepository<Group, UUID> {

  /**
   * Find a group by ID with related entities eagerly fetched.
   *
   * @param id the group ID
   * @return the group with eagerly fetched contactUser and parentGroup
   */
  @EntityGraph(attributePaths = {"contactUser", "parentGroup"})
  @Query("SELECT g FROM Group g WHERE g.id = :id")
  Optional<Group> findByIdWithRelations(@Param("id") UUID id);

  /**
   * Find groups by IDs with members eagerly fetched.
   *
   * @param ids the group IDs
   * @return the groups with eagerly fetched members
   */
  @EntityGraph(attributePaths = {"members"})
  @Query("SELECT g FROM Group g WHERE g.id IN :ids")
  List<Group> findAllByIdWithMembers(@Param("ids") List<UUID> ids);

  /**
   * Count groups by IDs.
   *
   * @param ids the group IDs
   * @return the count of groups found
   */
  @Query("SELECT COUNT(g) FROM Group g WHERE g.id IN :ids")
  long countByIdIn(@Param("ids") List<UUID> ids);
}
