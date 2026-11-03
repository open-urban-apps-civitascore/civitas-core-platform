package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.repository.ReferrerReleaseState;
import de.civitascore.portal.util.ResourceInUseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Component;

/**
 * Answers, for a Data source, a Data structure or a Data structure version, whether anything
 * references it and whether a released entity does. The first is only reported; the second decides
 * the unrelease and which fields a released artifact may still change.
 *
 * <p>Only direct referrers are judged. Releasing an entity requires everything it references to be
 * released, so a DRAFT artifact has no released referrer and is answered without classifying one. A
 * Mapping has no row and no status, so it counts as released when a Pipeline or Dataset that
 * references it does. A Data structure has no status of its own; it counts as released when one of
 * its versions is.
 *
 * <p>A Dataset counts as released until its unrelease saga completes, because a failed saga
 * restores it to AVAILABLE.
 *
 * <p>A referrer the platform keeps no row for counts as released: the unrelease it guards then
 * fails closed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArtifactUsageLookup {

  private static final String PIPELINE = "pipeline";
  private static final String DATA_SINK = "datasink";
  private static final String DATA_SOURCE = "datasource";
  private static final String DATA_SET = "dataset";
  private static final String DATA_STRUCTURE = "datastructure";
  private static final String ELEMENT = "element";
  private static final String MAPPING = "mapping";
  private static final String DATA_SOURCE_BLOCKER = "DataSource ";

  private final ModelRegistryGateway modelRegistryGateway;
  private final PipelineRepository pipelineRepository;
  private final DataSourceRepository dataSourceRepository;
  private final DataSinkRepository dataSinkRepository;
  private final DataSetRepository dataSetRepository;
  private final DataStructureRepository dataStructureRepository;
  private final DataStructureVersionRepository dataStructureVersionRepository;

  /**
   * How an artifact is used.
   *
   * @param inUse whether anything references the artifact
   * @param referrers what a refused delete logs as blocking it: the CORE URNs that reference the
   *     artifact in the model registry and, for a Data structure version, the Data sources pinned
   *     to it and the model pins of the versions in its line; empty for a Data source
   * @param releasedReferrers the released entities among the referrers; empty when none is
   */
  public record ArtifactUsage(
      boolean inUse, List<String> referrers, List<ReleasedReferrer> releasedReferrers) {

    public ArtifactUsage {
      referrers = List.copyOf(referrers);
      releasedReferrers = List.copyOf(releasedReferrers);
    }

    public boolean inUseByReleased() {
      return !releasedReferrers.isEmpty();
    }

    /** The usage of several artifacts taken together; unused when there are none. */
    public static ArtifactUsage anyOf(Collection<ArtifactUsage> usages) {
      return new ArtifactUsage(
          usages.stream().anyMatch(ArtifactUsage::inUse),
          usages.stream().flatMap(usage -> usage.referrers().stream()).distinct().toList(),
          usages.stream().flatMap(usage -> usage.releasedReferrers().stream()).distinct().toList());
    }

    /**
     * Refuses the delete of the artifact while anything references it.
     *
     * @throws ResourceInUseException if anything references the artifact
     */
    public void requireNotInUse(String resourceType, UUID resourceId) {
      if (inUse) {
        throw new ResourceInUseException(
            resourceType,
            resourceId,
            "Cannot delete " + resourceType + " because it is in use.",
            referrers);
      }
    }

    /**
     * Refuses the unrelease of the artifact while a released entity references it.
     *
     * @throws ResourceInUseException if there is a released referrer; its message names what the
     *     first one is
     */
    public void requireNoReleasedReferrer(String resourceType, UUID resourceId) {
      if (!inUseByReleased()) {
        return;
      }
      throw new ResourceInUseException(
          resourceType,
          resourceId,
          "Cannot unrelease "
              + resourceType
              + " because it is referenced by "
              + releasedReferrers.getFirst().kind().description()
              + ".",
          releasedReferrers.stream().map(ReleasedReferrer::reference).toList());
    }
  }

  /**
   * How a Data structure and each of its versions are used.
   *
   * @param structure what deleting the Data structure would remove: the versions' usage from
   *     outside it, since versions that share a model line are deleted together
   * @param versions each version's usage, keyed by version id, as its own delete judges it
   */
  public record DataStructureUsage(ArtifactUsage structure, Map<UUID, ArtifactUsage> versions) {

    public DataStructureUsage {
      versions = Map.copyOf(versions);
    }
  }

  /**
   * A released entity that references the artifact.
   *
   * @param kind what the referrer is
   * @param reference its id or CORE URN
   */
  public record ReleasedReferrer(ReferrerKind kind, String reference) {}

  /** What a released referrer is. */
  public enum ReferrerKind {
    DATA_SOURCE("a released Data source"),
    PIPELINE("a Pipeline of a released Dataset"),
    DATA_SINK("a Data sink of a released Dataset"),
    MAPPING("a Mapping used by a released Dataset"),
    DATA_SET("a released Dataset"),
    DATA_STRUCTURE("a Data structure with a released version"),
    UNKNOWN("an artifact the platform keeps no record of");

    private final String description;

    ReferrerKind(String description) {
      this.description = description;
    }

    public String description() {
      return description;
    }
  }

  /** How a Data source is used. Its referrers are the Pipelines linking it. */
  public ArtifactUsage of(DataSource dataSource) {
    boolean inUse = pipelineRepository.existsByDataSourcesId(dataSource.getId());
    if (!inUse || dataSource.getDataSourceStatus() != DataSourceStatus.AVAILABLE) {
      return new ArtifactUsage(inUse, List.of(), List.of());
    }
    List<ReleasedReferrer> released =
        pipelineRepository.findIdsByDataSourceIdWithReleasedDataSet(dataSource.getId()).stream()
            .map(pipelineId -> new ReleasedReferrer(ReferrerKind.PIPELINE, pipelineId.toString()))
            .toList();
    return new ArtifactUsage(true, List.of(), released);
  }

  /** How a Data structure is used: through any of its versions. */
  public ArtifactUsage of(DataStructure dataStructure) {
    return ofStructureAndVersions(dataStructure).structure();
  }

  /** How a Data structure and each of its versions are used. */
  public DataStructureUsage ofStructureAndVersions(DataStructure dataStructure) {
    Set<DataStructureVersion> versions = dataStructure.getDataStructureVersions();
    Map<UUID, ArtifactUsage> fromOutside = new LinkedHashMap<>();
    versions.forEach(version -> fromOutside.put(version.getId(), usageFromOutside(version)));
    Map<UUID, ArtifactUsage> byVersion = new LinkedHashMap<>();
    versions.forEach(
        version ->
            byVersion.put(
                version.getId(),
                withModelLineSharers(
                    fromOutside.get(version.getId()), modelLineSharersAmong(version, versions))));
    return new DataStructureUsage(ArtifactUsage.anyOf(fromOutside.values()), byVersion);
  }

  /**
   * How one Data structure version is used: by anything that references any version of its line in
   * the model registry, by the Data sources pinned to it, and by the other versions of its Data
   * structure whose model is in the same line. Deleting the version would remove all of that.
   */
  public ArtifactUsage of(DataStructureVersion version) {
    return withModelLineSharers(usageFromOutside(version), modelLineSharersOf(version));
  }

  /**
   * Refuses the delete of a Data structure version while anything uses it.
   *
   * @throws ResourceInUseException if anything uses the version; when only other versions of its
   *     Data structure share its model line, the message says that only the Data structure delete
   *     removes such versions
   */
  public void requireNotInUse(DataStructureVersion version, String resourceType) {
    ArtifactUsage fromOutside = usageFromOutside(version);
    List<String> sharers = modelLineSharersOf(version);
    if (!fromOutside.inUse() && !sharers.isEmpty()) {
      throw new ResourceInUseException(
          resourceType,
          version.getId(),
          "Cannot delete "
              + resourceType
              + " because another version of its data structure shares its model; only deleting"
              + " the whole data structure deletes such versions.",
          sharers);
    }
    withModelLineSharers(fromOutside, sharers).requireNotInUse(resourceType, version.getId());
  }

  private ArtifactUsage usageFromOutside(DataStructureVersion version) {
    List<String> registryReferrers =
        modelRegistryGateway.versionReferencesTo(version.getModelUrn()).stream()
            .distinct()
            .toList();
    List<String> referrers = new ArrayList<>(registryReferrers);
    dataSourceRepository
        .findIdsByDataStructureVersionId(version.getId())
        .forEach(id -> referrers.add(DATA_SOURCE_BLOCKER + id));
    if (version.getDataStructureVersionStatus() != DataStructureVersionStatus.AVAILABLE) {
      return new ArtifactUsage(!referrers.isEmpty(), referrers, List.of());
    }

    List<ReleasedReferrer> releasedReferrers = new ArrayList<>();
    dataSourceRepository
        .findIdsByDataStructureVersionIdInAndStatus(
            Set.of(version.getId()), DataSourceStatus.AVAILABLE)
        .forEach(
            id ->
                releasedReferrers.add(
                    new ReleasedReferrer(ReferrerKind.DATA_SOURCE, id.toString())));
    releasedReferrers.addAll(releasedAmong(registryReferrers, true));
    return new ArtifactUsage(!referrers.isEmpty(), referrers, releasedReferrers);
  }

  private List<String> modelLineSharersOf(DataStructureVersion version) {
    if (version.getModelUrn() == null) {
      return List.of();
    }
    return modelLineSharersAmong(
        version,
        dataStructureVersionRepository.findAllByDataStructure_IdIn(
            List.of(version.getDataStructure().getId())));
  }

  /** The model pins of the other versions among {@code versions} in the version's model line. */
  private static List<String> modelLineSharersAmong(
      DataStructureVersion version, Collection<DataStructureVersion> versions) {
    String pin = version.getModelUrn();
    return versions.stream()
        .filter(other -> !other.getId().equals(version.getId()))
        .map(DataStructureVersion::getModelUrn)
        .filter(otherPin -> ModelRegistryGateway.sameLine(pin, otherPin))
        .toList();
  }

  private static ArtifactUsage withModelLineSharers(ArtifactUsage usage, List<String> sharers) {
    if (sharers.isEmpty()) {
      return usage;
    }
    List<String> referrers = new ArrayList<>(usage.referrers());
    referrers.addAll(sharers);
    return new ArtifactUsage(true, referrers, usage.releasedReferrers());
  }

  /**
   * The released entities among registry referrers, one database query per referrer kind.
   *
   * @param followMappings whether a Mapping referrer is judged by what references it in turn
   */
  private List<ReleasedReferrer> releasedAmong(
      Collection<String> referrerUrns, boolean followMappings) {
    Map<String, List<String>> byType = new LinkedHashMap<>();
    for (String urn : referrerUrns) {
      byType
          .computeIfAbsent(
              Objects.requireNonNullElse(modelRegistryGateway.artifactType(urn), ""),
              type -> new ArrayList<>())
          .add(urn);
    }
    List<ReleasedReferrer> released = new ArrayList<>();
    List<String> structureReferrers = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : byType.entrySet()) {
      List<String> urns = entry.getValue();
      switch (entry.getKey()) {
        case PIPELINE ->
            released.addAll(
                releasedRows(
                    ReferrerKind.PIPELINE,
                    identity(urns),
                    found -> pipelineRepository.findReleaseStatesByModelLogicalUrnIn(found)));
        case DATA_SINK ->
            released.addAll(
                releasedRows(
                    ReferrerKind.DATA_SINK,
                    identity(urns),
                    found ->
                        dataSinkRepository.findReleaseStatesByConfigurationLogicalUrnIn(found)));
        case DATA_SOURCE ->
            released.addAll(
                releasedRows(
                    ReferrerKind.DATA_SOURCE,
                    identity(urns),
                    found ->
                        dataSourceRepository.findReleaseStatesByConfigurationLogicalUrnIn(
                            found, DataSourceStatus.AVAILABLE)));
        case DATA_SET ->
            released.addAll(
                releasedRows(
                    ReferrerKind.DATA_SET,
                    identity(urns),
                    found -> dataSetRepository.findReleaseStatesByManifestLogicalUrnIn(found)));
        case DATA_STRUCTURE, ELEMENT -> structureReferrers.addAll(urns);
        case MAPPING -> released.addAll(followMappings ? releasedMappings(urns) : unknown(urns));
        default -> released.addAll(unknown(urns));
      }
    }
    if (!structureReferrers.isEmpty()) {
      Map<String, Set<String>> hostsByReferrer = new LinkedHashMap<>();
      structureReferrers.forEach(
          urn -> hostsByReferrer.put(urn, modelRegistryGateway.hostModelUrnsOf(urn)));
      released.addAll(
          releasedRows(
              ReferrerKind.DATA_STRUCTURE,
              hostsByReferrer,
              found ->
                  dataStructureRepository.findReleaseStatesByModelLogicalUrnIn(
                      found, DataStructureVersionStatus.AVAILABLE)));
    }
    return released;
  }

  /**
   * The Mappings a released Pipeline or Dataset references. One registry call per Mapping, then one
   * database query per kind across all of them.
   */
  private List<ReleasedReferrer> releasedMappings(List<String> mappingUrns) {
    Map<String, Set<String>> mappingsByUser = new HashMap<>();
    for (String mapping : mappingUrns) {
      for (String user : modelRegistryGateway.referencesTo(mapping)) {
        mappingsByUser.computeIfAbsent(user, key -> new LinkedHashSet<>()).add(mapping);
      }
    }
    Set<String> released = new LinkedHashSet<>();
    for (ReleasedReferrer user : releasedAmong(mappingsByUser.keySet(), false)) {
      released.addAll(mappingsByUser.get(user.reference()));
    }
    return released.stream()
        .map(mapping -> new ReleasedReferrer(ReferrerKind.MAPPING, mapping))
        .toList();
  }

  /**
   * The referrers with a released host row, plus those with no host row at all.
   *
   * @param hostsByReferrer each referrer's URN mapped to the logical URNs its host row may be found
   *     by
   */
  private List<ReleasedReferrer> releasedRows(
      ReferrerKind kind,
      Map<String, Set<String>> hostsByReferrer,
      Function<Collection<String>, List<ReferrerReleaseState>> lookup) {
    Set<String> allHosts = new HashSet<>();
    hostsByReferrer.values().forEach(allHosts::addAll);
    Map<String, Boolean> releasedByHost = new HashMap<>();
    for (ReferrerReleaseState state : lookup.apply(allHosts)) {
      releasedByHost.merge(state.logicalUrn(), state.released(), Boolean::logicalOr);
    }
    List<ReleasedReferrer> released = new ArrayList<>();
    hostsByReferrer.forEach(
        (referrer, hosts) -> {
          Boolean state =
              hosts.stream()
                  .map(releasedByHost::get)
                  .filter(Objects::nonNull)
                  .reduce(Boolean::logicalOr)
                  .orElse(null);
          if (state == null) {
            released.addAll(unknown(List.of(referrer)));
          } else if (state) {
            released.add(new ReleasedReferrer(kind, referrer));
          }
        });
    return released;
  }

  private List<ReleasedReferrer> unknown(List<String> urns) {
    return urns.stream()
        .map(
            urn -> {
              log.warn(
                  "Usage check: referrer {} maps to no platform record and counts as released",
                  Encode.forJava(urn));
              return new ReleasedReferrer(ReferrerKind.UNKNOWN, urn);
            })
        .toList();
  }

  private static Map<String, Set<String>> identity(List<String> urns) {
    Map<String, Set<String>> identity = new LinkedHashMap<>();
    urns.forEach(urn -> identity.put(urn, Set.of(urn)));
    return identity;
  }
}
