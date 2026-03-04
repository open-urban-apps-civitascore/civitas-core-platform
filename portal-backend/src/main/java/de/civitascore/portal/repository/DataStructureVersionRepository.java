package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataStructureVersion;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

  Set<DataStructureVersion> findAllByDataStructureIdAndVersion(UUID id, String version);
}
