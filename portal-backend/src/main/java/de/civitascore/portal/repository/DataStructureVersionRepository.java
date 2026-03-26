package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataStructureVersion;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link DataStructureVersion} entities. */
@Repository
public interface DataStructureVersionRepository extends BaseRepository<DataStructureVersion, UUID> {

  /**
   * Find a data structure version by ID with related entities eagerly fetched. This prevents N+1
   * query problems when loading data structure versions with their relationships.
   *
   * @param id the data structure version ID
   * @return the data structure version with eagerly fetched dataStructure
   */
  @EntityGraph(attributePaths = {"dataStructure"})
  @Query("SELECT dsv FROM DataStructureVersion dsv WHERE dsv.id = :id")
  Optional<DataStructureVersion> findByIdWithRelations(@Param("id") UUID id);

  /**
   * Find all data structure versions matching the given data structure ID and version string. Used
   * for uniqueness validation when creating new versions.
   *
   * @param id the parent data structure ID
   * @param version the version string to match
   * @return matching data structure versions
   */
  Set<DataStructureVersion> findAllByDataStructureIdAndVersion(UUID id, String version);
}
