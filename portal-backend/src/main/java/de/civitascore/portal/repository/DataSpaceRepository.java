package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataSpace;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DataSpaceRepository extends NamedEntityRepository<DataSpace, UUID> {

  /**
   * Find a dataspace by ID with related entities eagerly fetched. This prevents N+1 query problems
   * when loading dataspaces with their relationships.
   *
   * @param id the dataspace ID
   * @return the dataspace with eagerly fetched owner and parentDataSpace
   */
  @EntityGraph(attributePaths = {"owner", "parentDataSpace"})
  @Query("SELECT d FROM DataSpace d WHERE d.id = :id")
  Optional<DataSpace> findByIdWithRelations(@Param("id") UUID id);
}
