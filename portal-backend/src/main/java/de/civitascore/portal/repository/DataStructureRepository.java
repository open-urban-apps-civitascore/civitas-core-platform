package de.civitascore.portal.repository;

import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.entity.DataStructure;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
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
  @Override
  @NonNull Optional<DataStructure> findById(@NonNull UUID id);

  /** Whether each data structure has the given status, keyed by its model's logical URN. */
  @Query(
      "SELECT new de.civitascore.portal.repository.ReferrerReleaseState(s.modelLogicalUrn,"
          + " CASE WHEN s.dataStructureStatus = :status THEN true ELSE false END)"
          + " FROM DataStructure s WHERE s.modelLogicalUrn IN :urns")
  List<ReferrerReleaseState> findReleaseStatesByModelLogicalUrnIn(
      @Param("urns") Collection<String> urns, @Param("status") DataStructureStatus status);
}
