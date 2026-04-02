package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataStructure;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link DataStructure} entities. */
@Repository
public interface DataStructureRepository extends NamedEntityRepository<DataStructure, UUID> {

  /**
   * Find a data structure by ID with related entities eagerly fetched. This prevents N+1 query
   * problems when loading data structures with their relationships.
   *
   * @param id the data structure ID
   * @return the data structure with eagerly fetched dataStructureVersions
   */
  @EntityGraph(attributePaths = {"dataStructureVersions"})
  @Query("SELECT ds FROM DataStructure ds WHERE ds.id = :id")
  Optional<DataStructure> findByIdWithRelations(@Param("id") UUID id);
}
