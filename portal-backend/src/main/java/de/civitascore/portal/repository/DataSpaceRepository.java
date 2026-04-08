package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataSpace;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link DataSpace} entities. */
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
  @Override
  @NonNull Optional<DataSpace> findById(@NonNull UUID id);
}
