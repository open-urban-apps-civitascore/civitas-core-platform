package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataStructureVersion;
import java.util.Collection;
import java.util.List;
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

  /**
   * Finds the version whose {@code modelUrn} is exactly the given versioned CORE URN — the pin a
   * DataSink's {@code element} carries — so a sink resolves to the precise version it references
   * rather than any version of the owning structure. {@code First} because the column carries no
   * unique constraint. The {@code dataStructure} is eagerly fetched so the caller can read its id
   * outside the persistence context.
   *
   * @param modelUrn the versioned CORE URN to match
   * @return the version pinning that URN, or empty if no stored version does
   */
  @EntityGraph(attributePaths = {"dataStructure"})
  Optional<DataStructureVersion> findFirstByModelUrn(String modelUrn);

  /**
   * Every version pinned by exactly {@code modelUrn}.
   *
   * <p>Distinct from {@link #findFirstByModelUrnStartingWith}, which matches any version of a
   * structure because a parent's identity is all an authorization decision needs. A release
   * lifecycle belongs to one version, so a check on it must name that version: matching
   * version-agnostically would let a flow pinned to a draft version pass on the status of a
   * different, released one.
   *
   * <p>Several rows can share one pin, so this returns all of them rather than one. The registry
   * returns the version it already holds when a stored model is byte-identical to it, so two
   * versions authored with the same content are pinned to the same artifact — and {@code model_urn}
   * carries no unique constraint. The {@code dataStructure} is eagerly fetched so the caller can
   * read each parent's own status outside the persistence context.
   *
   * @param modelUrn the versioned CORE URN a flow pins
   * @return every version pinned by that URN, empty if the platform holds no record of it
   */
  @EntityGraph(attributePaths = {"dataStructure"})
  List<DataStructureVersion> findAllByModelUrn(String modelUrn);

  /**
   * Every version pinned by any of {@code modelUrns} — the bulk form of {@link #findAllByModelUrn},
   * so validating a whole flow is one query rather than one per artifact.
   *
   * <p>A URN with no row here is one the platform holds no lifecycle record for, which is what
   * distinguishes an artifact it governs from one it merely stores.
   *
   * @param modelUrns the versioned CORE URNs a flow pins
   * @return every version pinning any of them, empty when none is known
   */
  @EntityGraph(attributePaths = {"dataStructure"})
  List<DataStructureVersion> findAllByModelUrnIn(Collection<String> modelUrns);
}
