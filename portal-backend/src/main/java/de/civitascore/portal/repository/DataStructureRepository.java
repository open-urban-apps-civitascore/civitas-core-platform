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
   * Whether any data structure shell already pins the given logical model URN. Used by the import
   * endpoint to reject re-imports of an already installed model identity (names are not unique; the
   * URN is the only identity shared between catalogue, registry and shell).
   *
   * @param modelLogicalUrn the logical (version-free) CORE URN
   * @return true if a shell with this pin exists
   */
  boolean existsByModelLogicalUrn(String modelLogicalUrn);
}
