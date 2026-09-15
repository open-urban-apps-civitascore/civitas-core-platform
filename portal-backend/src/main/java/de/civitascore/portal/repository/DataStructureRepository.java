package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataStructure;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
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
  @Override
  @NonNull Optional<DataStructure> findById(@NonNull UUID id);

  /**
   * Whether a shell already pins this model identity. The registry itself is idempotent — the same
   * URN resolves to the same artifact — but the shell layer is not: without this guard a repeated
   * install would add another row pointing at the same artifact.
   *
   * @param modelLogicalUrn the version-free model URN
   * @return true if a data structure already pins it
   */
  boolean existsByModelLogicalUrn(String modelLogicalUrn);
}
