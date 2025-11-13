package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Group;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends NamedEntityRepository<Group, String> {

  /**
   * Find a group by ID with related entities eagerly fetched.
   *
   * @param id the group ID
   * @return the group with eagerly fetched contactUser and parentGroup
   */
  @EntityGraph(attributePaths = {"contactUser", "parentGroup"})
  @Query("SELECT g FROM Group g WHERE g.id = :id")
  Optional<Group> findByIdWithRelations(@Param("id") String id);
}
