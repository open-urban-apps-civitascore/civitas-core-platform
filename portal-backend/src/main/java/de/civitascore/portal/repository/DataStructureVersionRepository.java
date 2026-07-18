package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataStructureVersion;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
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
  @Override
  @NonNull Optional<DataStructureVersion> findById(@NonNull UUID id);

  /**
   * Find all data structure versions matching the given data structure ID and version string. Used
   * for uniqueness validation when creating new versions.
   *
   * @param id the parent data structure ID
   * @param version the version string to match
   * @return matching data structure versions
   */
  Set<DataStructureVersion> findAllByDataStructureIdAndVersion(UUID id, String version);

  /**
   * Finds any version whose {@code modelUrn} begins with the given prefix — typically the logical
   * ({@code version}-free) CORE URN of a DataStructure followed by {@code ":"}, so it matches every
   * stored version of that structure. Used to resolve a versioned {@code :datastructure:} URN
   * referenced by a DataSink back to its owning portal DataStructure for authorization: every
   * version shares the same parent, so the first match suffices. The {@code dataStructure} is
   * eagerly fetched so the caller can read its id outside the persistence context.
   *
   * @param modelUrnPrefix the {@code modelUrn} prefix to match (e.g. {@code logicalUrn + ":"})
   * @return a matching version, or empty if no stored version references that structure
   */
  @EntityGraph(attributePaths = {"dataStructure"})
  Optional<DataStructureVersion> findFirstByModelUrnStartingWith(String modelUrnPrefix);
}
