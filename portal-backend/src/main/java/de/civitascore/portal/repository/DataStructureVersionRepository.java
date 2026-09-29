package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataStructureVersion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

  /**
   * Every version record of the structures these version-free URNs name.
   *
   * <p>A flow pins one version, but a draft version's pin advances inside its own major whenever
   * its model changes, so the pinned string stops naming any record. Resolving by structure and
   * major finds the record that owns the pin, while a later major stays out of reach because it is
   * a different record with a lifecycle of its own.
   *
   * @param modelLogicalUrns the version-free CORE URNs of the owning structures
   * @return every version record of those structures, parents eagerly fetched
   */
  @EntityGraph(attributePaths = {"dataStructure"})
  List<DataStructureVersion> findAllByDataStructure_ModelLogicalUrnIn(
      Collection<String> modelLogicalUrns);
}
